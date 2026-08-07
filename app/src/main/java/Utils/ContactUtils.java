package Utils;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactUtils {
    public static class Contact {
        public String name;
        public String phoneNumber;
        public String email;
        public String relationship;
        public boolean isEmergencyContact;

        public Contact(String name, String phoneNumber, String email) {
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.email = email;
            this.relationship = "";
            this.isEmergencyContact = false;
        }
    }

    public static List<Contact> getContacts(Context context) {
        List<Contact> contacts = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return contacts;
        }

        ContentResolver cr = context.getContentResolver();
        Map<String, Contact> contactMap = new HashMap<>();

        // Get names and contact IDs
        try (Cursor cursor = cr.query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                },
                null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                    int hasPhone = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER));

                    Contact contact = new Contact(name, "", "");
                    contactMap.put(id, contact);

                    // Get phone numbers
                    if (hasPhone > 0) {
                        try (Cursor phones = cr.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                new String[]{id}, null)) {
                            if (phones != null && phones.moveToFirst()) {
                                contact.phoneNumber = phones.getString(phones.getColumnIndexOrThrow(
                                        ContactsContract.CommonDataKinds.Phone.NUMBER));
                            }
                        }
                    }

                    // Get emails
                    try (Cursor emails = cr.query(
                            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                            new String[]{ContactsContract.CommonDataKinds.Email.DATA},
                            ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                            new String[]{id}, null)) {
                        if (emails != null && emails.moveToFirst()) {
                            contact.email = emails.getString(emails.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Email.DATA));
                        }
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        contacts.addAll(contactMap.values());
        return contacts;
    }
}