package Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;

import java.util.ArrayList;
import java.util.List;

import Models.AiMemory;

/**
 * Lists the durable facts Richie remembers about the user. Each row shows the
 * category tag and the fact, with a delete action wired back to the fragment.
 */
public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.MemoryViewHolder> {

    private List<AiMemory> memories;
    private final Context context;
    private OnMemoryDeleteListener deleteListener;

    public interface OnMemoryDeleteListener {
        void onDelete(AiMemory memory, int position);
    }

    public MemoryAdapter(Context context) {
        this.context = context;
        this.memories = new ArrayList<>();
    }

    public void setDeleteListener(OnMemoryDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setData(List<AiMemory> data) {
        this.memories = data != null ? data : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < memories.size()) {
            memories.remove(position);
            notifyItemRemoved(position);
        }
    }

    public int getItemCountSafe() {
        return memories != null ? memories.size() : 0;
    }

    @NonNull
    @Override
    public MemoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_ai_memory, parent, false);
        return new MemoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemoryViewHolder holder, int position) {
        AiMemory memory = memories.get(position);
        holder.categoryText.setText(memory.getCategory() != null && !memory.getCategory().isEmpty()
                ? memory.getCategory() : "general");
        holder.factText.setText(memory.getFact());
        holder.deleteButton.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(memory, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return memories.size();
    }

    static class MemoryViewHolder extends RecyclerView.ViewHolder {
        TextView categoryText;
        TextView factText;
        ImageView deleteButton;

        MemoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryText = itemView.findViewById(R.id.memory_category);
            factText = itemView.findViewById(R.id.memory_fact);
            deleteButton = itemView.findViewById(R.id.memory_delete);
        }
    }
}
