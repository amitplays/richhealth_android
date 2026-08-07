package Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import Models.ChatMessage;

public class SavedChatAdapter extends RecyclerView.Adapter<SavedChatAdapter.SavedChatViewHolder> {
    private List<ChatMessage> savedMessages;
    private Context context;
    private OnSavedChatActionListener actionListener;

    public interface OnSavedChatActionListener {
        void onDeleteSavedChat(ChatMessage message);
    }

    public SavedChatAdapter(Context context) {
        this.context = context;
        this.savedMessages = new ArrayList<>();
    }

    public void setActionListener(OnSavedChatActionListener listener) {
        this.actionListener = listener;
    }

    public void setSavedMessages(List<ChatMessage> messages) {
        this.savedMessages = messages;
        notifyDataSetChanged();
    }

    public void removeMessage(String messageId) {
        for (int i = 0; i < savedMessages.size(); i++) {
            if (savedMessages.get(i).getMessageId().equals(messageId)) {
                savedMessages.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public SavedChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_saved_chat, parent, false);
        return new SavedChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SavedChatViewHolder holder, int position) {
        ChatMessage message = savedMessages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return savedMessages.size();
    }

    class SavedChatViewHolder extends RecyclerView.ViewHolder {
        private TextView messagePreview;
        private TextView savedDate;
        private MaterialButton deleteButton;
        private MaterialButton viewChatButton;

        public SavedChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messagePreview = itemView.findViewById(R.id.message_preview);
            savedDate = itemView.findViewById(R.id.saved_date);
            deleteButton = itemView.findViewById(R.id.delete_button);
            viewChatButton = itemView.findViewById(R.id.view_chat_button);
        }

        public void bind(ChatMessage message) {
            // Show ellipsized preview
            String preview = message.getMessage();
            if (preview.length() > 100) {
                preview = preview.substring(0, 100) + "...";
            }
            messagePreview.setText(preview);

            // Format saved date
            if (message.getSavedAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                savedDate.setText("Saved on " + sdf.format(message.getSavedAt()));
            }

            // View button click listener - Replaces the itemView click functionality
            viewChatButton.setOnClickListener(v -> showFullMessage(message));

            // Delete button click listener
            deleteButton.setOnClickListener(v -> {
                Utils.DialogUtils.showConfirmDialog(context,
                        "Delete Saved Chat",
                        "Are you sure you want to remove this from saved chats?",
                        "Delete", "Cancel", true,
                        () -> {
                            if (actionListener != null) {
                                actionListener.onDeleteSavedChat(message);
                            }
                        });
            });
        }

        private void showFullMessage(ChatMessage message) {
            Utils.DialogUtils.showConfirmDialog(context,
                    "Saved Chat",
                    message.getMessage(),
                    "Close", null, false, null);
        }
    }
}