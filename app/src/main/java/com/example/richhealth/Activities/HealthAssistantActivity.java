package com.example.richhealth.Activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.richhealth.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HealthAssistantActivity extends AppCompatActivity {

    private RecyclerView chatRecycler;
    private EditText messageInput;
    private List<Map<String, String>> chatMessages;
    private MentalHealthChatAdapter chatAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_assistant);

        // Back button
        ImageButton backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        messageInput = findViewById(R.id.message_input);
        MaterialButton sendButton = findViewById(R.id.send_button);

        chatRecycler = findViewById(R.id.chat_recycler);
        chatRecycler.setLayoutManager(new LinearLayoutManager(this));

        // Create chat adapter
        chatMessages = new ArrayList<>();
        chatAdapter = new MentalHealthChatAdapter(chatMessages);
        chatRecycler.setAdapter(chatAdapter);

        // Add welcome message
        Map<String, String> welcomeMessage = new HashMap<>();
        welcomeMessage.put("text", "Hello! I'm your mental health assistant. How are you feeling today?");
        welcomeMessage.put("sender", "assistant");
        chatMessages.add(welcomeMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);

        // Set up send button
        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                // Add user message
                Map<String, String> userMessage = new HashMap<>();
                userMessage.put("text", message);
                userMessage.put("sender", "user");
                chatMessages.add(userMessage);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);

                // Clear input
                messageInput.setText("");

                // Scroll to bottom
                chatRecycler.smoothScrollToPosition(chatMessages.size() - 1);

                // Simulate AI response
                new android.os.Handler().postDelayed(() -> {
                    String response = generateMentalHealthResponse(message);

                    Map<String, String> aiResponse = new HashMap<>();
                    aiResponse.put("text", response);
                    aiResponse.put("sender", "assistant");
                    chatMessages.add(aiResponse);
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);

                    chatRecycler.smoothScrollToPosition(chatMessages.size() - 1);
                }, 1000);
            }
        });
    }

    private String generateMentalHealthResponse(String message) {
        String messageLower = message.toLowerCase();

        if (messageLower.contains("stress") || messageLower.contains("stressed") ||
                messageLower.contains("anxious") || messageLower.contains("anxiety")) {
            return "I'm sorry to hear you're feeling stressed. Consider trying deep breathing exercises, " +
                    "progressive muscle relaxation, or a short mindfulness meditation. " +
                    "What specifically is causing this feeling?";
        }
        else if (messageLower.contains("sad") || messageLower.contains("depression") ||
                messageLower.contains("depressed") || messageLower.contains("unhappy")) {
            return "I understand that feeling sad can be difficult. Consider connecting with a friend, " +
                    "engaging in a physical activity, or practicing self-care. Would you like to talk more about what's causing these feelings?";
        }
        else if (messageLower.contains("tired") || messageLower.contains("exhausted") ||
                messageLower.contains("fatigue") || messageLower.contains("sleep")) {
            return "Fatigue can have many causes. Are you getting 7-9 hours of quality sleep? " +
                    "Consider reviewing your sleep habits, physical activity, and stress levels. " +
                    "Would you like some tips for better sleep?";
        }
        else if (messageLower.contains("good") || messageLower.contains("great") ||
                messageLower.contains("fine") || messageLower.contains("well")) {
            return "I'm glad to hear you're doing well! Maintaining positive mental health is important. " +
                    "Is there anything specific you'd like to discuss or any area of your well-being you'd like to focus on?";
        }
        else {
            return "Thank you for sharing. Remember that your feelings are valid. " +
                    "Would you like to talk more about this, or would you prefer some resources on maintaining mental well-being?";
        }
    }

    // Chat adapter (moved from HomeFragment)
    private class MentalHealthChatAdapter extends RecyclerView.Adapter<MentalHealthChatAdapter.ChatViewHolder> {
        private List<Map<String, String>> messages;

        public MentalHealthChatAdapter(List<Map<String, String>> messages) {
            this.messages = messages;
        }

        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message, parent, false);
            return new ChatViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            Map<String, String> message = messages.get(position);
            holder.messageText.setText(message.get("text"));

            if ("user".equals(message.get("sender"))) {
                holder.messageText.setBackgroundResource(R.drawable.chat_bubble_user);
                holder.itemView.setPadding(80, 8, 8, 8);
            } else {
                holder.messageText.setBackgroundResource(R.drawable.chat_bubble_assistant);
                holder.itemView.setPadding(8, 8, 80, 8);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class ChatViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;

            public ChatViewHolder(@NonNull View itemView) {
                super(itemView);
                messageText = itemView.findViewById(R.id.message_text);
            }
        }
    }
}
