package Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns the family payloads the backend exposes into a generation-layered graph
 * the tree view can draw.
 *
 * IMPORTANT — what this is and is not. The backend stores family as a FLAT list:
 * every relative is described only in relation to the signed-in user ("Father",
 * "Maternal Aunt", ...). There is no relative-to-relative edge data, so nothing
 * records that your father and mother are a couple, or which side a grandparent
 * belongs to. What we can build is therefore a hub graph laid out BY GENERATION,
 * inferred from the relationship label alone — not a genealogical tree. Row
 * position is the only claim it makes.
 *
 * Sources merged (the dependent pair is the same one the chat picker uses):
 *   GET /api/user/relationships  → accepted relatives + pending invites
 *   GET /api/dependents/users    → living dependents held as User records
 *   GET /api/dependents          → child / deceased dependent profiles
 */
public final class FamilyGraph {

    private FamilyGraph() {}

    // ── Generation offsets from the signed-in user (lower = older) ──────────
    public static final int GEN_GRANDPARENT = -2;
    public static final int GEN_PARENT      = -1;
    public static final int GEN_SELF        =  0;
    public static final int GEN_CHILD       =  1;
    public static final int GEN_GRANDCHILD  =  2;

    /**
     * A relationship we cannot place on a generation row — "Other", the legacy
     * "Family Member" fallback, or a deceased dependent (those records carry no
     * relationship field at all). Drawn in a separate strip rather than guessed at.
     */
    public static final int GEN_UNPLACED = Integer.MIN_VALUE;

    /** One person in the graph. */
    public static final class Node {
        public String id = "";
        public String name = "";
        public String relationship = "";
        public String email = "";
        public int generation = GEN_UNPLACED;
        public boolean isSelf;
        public boolean isPro;
        public boolean isPending;     // invite sent / received, not yet accepted
        public boolean isDeceased;
        public boolean isDependent;
        /** Lower sorts closer to the centre of its row. */
        public int order = 100;

        public String initials() {
            String n = name == null ? "" : name.trim();
            if (n.isEmpty()) {
                String e = email == null ? "" : email.trim();
                return e.isEmpty() ? "?" : e.substring(0, 1).toUpperCase(Locale.US);
            }
            String[] parts = n.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length && sb.length() < 2; i++) {
                if (!parts[i].isEmpty()) sb.append(parts[i].charAt(0));
            }
            return sb.toString().toUpperCase(Locale.US);
        }

        /** Best available display name — falls back to the invite email. */
        public String displayName() {
            if (name != null && !name.trim().isEmpty()) return name.trim();
            if (email != null && !email.trim().isEmpty()) return email.trim();
            return "Unknown";
        }
    }

    // ── Relationship → generation ──────────────────────────────────────────
    // Keys are lower-cased. Covers every option in both apps' pickers plus the
    // gender-neutral labels the backend emits when a relative's gender is unset.
    private static final Map<String, Integer> GENERATION = new HashMap<>();
    // Lower value = drawn closer to the centre of its row.
    private static final Map<String, Integer> ORDER = new HashMap<>();

    static {
        put("grandfather",     GEN_GRANDPARENT, 0);
        put("grandmother",     GEN_GRANDPARENT, 0);
        put("grandparent",     GEN_GRANDPARENT, 1);

        put("father",          GEN_PARENT, 0);
        put("mother",          GEN_PARENT, 0);
        put("parent",          GEN_PARENT, 1);
        put("paternal uncle",  GEN_PARENT, 2);
        put("paternal aunt",   GEN_PARENT, 2);
        put("maternal uncle",  GEN_PARENT, 2);
        put("maternal aunt",   GEN_PARENT, 2);
        put("uncle",           GEN_PARENT, 3);
        put("aunt",            GEN_PARENT, 3);
        put("aunt/uncle",      GEN_PARENT, 3);

        put("spouse",          GEN_SELF, 0);
        put("husband",         GEN_SELF, 0);
        put("wife",            GEN_SELF, 0);
        put("brother",         GEN_SELF, 1);
        put("sister",          GEN_SELF, 1);
        put("sibling",         GEN_SELF, 1);
        put("cousin",          GEN_SELF, 2);

        put("son",             GEN_CHILD, 0);
        put("daughter",        GEN_CHILD, 0);
        put("child",           GEN_CHILD, 1);
        put("nephew",          GEN_CHILD, 2);
        put("niece",           GEN_CHILD, 2);
        put("nephew/niece",    GEN_CHILD, 2);

        put("grandson",        GEN_GRANDCHILD, 0);
        put("granddaughter",   GEN_GRANDCHILD, 0);
        put("grandchild",      GEN_GRANDCHILD, 1);
    }

    private static void put(String key, int generation, int order) {
        GENERATION.put(key, generation);
        ORDER.put(key, order);
    }

    /** GEN_UNPLACED when the label carries no generation we can trust. */
    public static int generationFor(String relationship) {
        if (relationship == null) return GEN_UNPLACED;
        Integer g = GENERATION.get(relationship.trim().toLowerCase(Locale.US));
        return g == null ? GEN_UNPLACED : g;
    }

    private static int orderFor(String relationship) {
        if (relationship == null) return 100;
        Integer o = ORDER.get(relationship.trim().toLowerCase(Locale.US));
        return o == null ? 100 : o;
    }

    /** Heading shown above each row. */
    public static String rowLabel(int generation) {
        switch (generation) {
            case GEN_GRANDPARENT: return "Grandparents";
            case GEN_PARENT:      return "Parents";
            case GEN_SELF:        return "You";
            case GEN_CHILD:       return "Children";
            case GEN_GRANDCHILD:  return "Grandchildren";
            default:              return "Other family";
        }
    }

    /** Generations in draw order, oldest first, unplaced last. */
    public static final int[] ROW_ORDER = {
            GEN_GRANDPARENT, GEN_PARENT, GEN_SELF, GEN_CHILD, GEN_GRANDCHILD, GEN_UNPLACED
    };

    // ── Building ───────────────────────────────────────────────────────────

    /**
     * Merge the three payloads into one node list. Any argument may be null — a
     * failed call simply contributes nothing, so a partial network result still
     * renders whatever we did get.
     *
     * @param selfName   signed-in user's name, for the centre node
     * @param relationships  body of GET /api/user/relationships
     * @param dependentUsers body of GET /api/dependents/users
     * @param dependents     body of GET /api/dependents
     */
    public static List<Node> build(String selfName,
                                   JSONObject relationships,
                                   JSONObject dependentUsers,
                                   JSONObject dependents) {
        List<Node> nodes = new ArrayList<>();

        Node self = new Node();
        self.id = "__self__";
        self.name = (selfName == null || selfName.trim().isEmpty()) ? "You" : selfName.trim();
        self.relationship = "You";
        self.generation = GEN_SELF;
        self.isSelf = true;
        self.order = -1;                 // always dead centre of its row
        nodes.add(self);

        // Identity keys already taken, so a person held in two sources is drawn once.
        Set<String> seen = new HashSet<String>();

        addRelationships(nodes, seen, relationships);
        addDependentUsers(nodes, seen, dependentUsers);
        addDependents(nodes, seen, dependents);

        sortRows(nodes);
        return nodes;
    }

    private static void addRelationships(List<Node> out, Set<String> seen, JSONObject payload) {
        if (payload == null) return;
        JSONArray arr = payload.optJSONArray("relationships");
        if (arr == null) return;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;

            Node n = new Node();
            n.id = o.optString("userId", "");
            n.name = o.optString("name", "");
            n.email = o.optString("email", "");
            n.relationship = o.optString("relationship", "");
            n.isPro = o.optBoolean("isPro", false);
            // getFamilyRelationships marks accepted relatives "accepted"; anything
            // else in that array is a pending invite (incoming or sent).
            n.isPending = !"accepted".equalsIgnoreCase(o.optString("status", ""));
            n.generation = generationFor(n.relationship);
            n.order = orderFor(n.relationship);

            if (claim(seen, n)) out.add(n);
        }
    }

    private static void addDependentUsers(List<Node> out, Set<String> seen, JSONObject payload) {
        if (payload == null) return;
        JSONArray arr = payload.optJSONArray("dependents");
        if (arr == null) return;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;

            Node n = new Node();
            n.id = o.optString("_id", "");
            n.name = o.optString("name", "");
            n.isDependent = true;

            // dependentType is the only relationship signal these records carry.
            String type = o.optString("dependentType", "");
            if ("child".equalsIgnoreCase(type)) {
                n.relationship = "Child";
                n.generation = GEN_CHILD;
                n.order = 1;
            } else if ("elder".equalsIgnoreCase(type)) {
                n.relationship = "Elder";
                n.generation = GEN_PARENT;
                n.order = 1;
            } else {
                n.relationship = "Dependent";
                n.generation = GEN_UNPLACED;
            }

            if (claim(seen, n)) out.add(n);
        }
    }

    private static void addDependents(List<Node> out, Set<String> seen, JSONObject payload) {
        if (payload == null) return;
        JSONArray arr = payload.optJSONArray("dependents");
        if (arr == null) return;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;

            Node n = new Node();
            n.id = o.optString("_id", "");
            n.name = o.optString("name", "");
            n.isDependent = true;

            String type = o.optString("type", "");
            if ("child".equalsIgnoreCase(type)) {
                n.relationship = "Child";
                n.generation = GEN_CHILD;
                n.order = 1;
            } else {
                // "deceased" profiles exist to carry hereditary history and hold no
                // relationship field, so there is nothing to place them on. Shown in
                // the Other family strip rather than guessed onto an ancestor row.
                n.relationship = "In memory";
                n.isDeceased = true;
                n.generation = GEN_UNPLACED;
            }

            if (claim(seen, n)) out.add(n);
        }
    }

    /** False when this person is already in the graph from an earlier source. */
    private static boolean claim(Set<String> seen, Node n) {
        String key = null;
        if (n.id != null && !n.id.isEmpty()) key = "id:" + n.id;
        else if (n.email != null && !n.email.isEmpty()) key = "em:" + n.email.toLowerCase(Locale.US);
        else if (n.name != null && !n.name.trim().isEmpty()) key = "nm:" + n.name.trim().toLowerCase(Locale.US);
        if (key == null) return false;   // nothing identifiable — drop it
        return seen.add(key);
    }

    /**
     * Within a row: closest relations nearest the centre, then alphabetical, with
     * pending invites last so the confirmed family reads first.
     */
    private static void sortRows(List<Node> nodes) {
        Collections.sort(nodes, new Comparator<Node>() {
            @Override public int compare(Node a, Node b) {
                if (a.isPending != b.isPending) return a.isPending ? 1 : -1;
                if (a.order != b.order) return a.order < b.order ? -1 : 1;
                return a.displayName().compareToIgnoreCase(b.displayName());
            }
        });
    }

    /** Everyone except the centre node — i.e. the number of family members. */
    public static int memberCount(List<Node> nodes) {
        int n = 0;
        for (int i = 0; i < nodes.size(); i++) if (!nodes.get(i).isSelf) n++;
        return n;
    }
}
