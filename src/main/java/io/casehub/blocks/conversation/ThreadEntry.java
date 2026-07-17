package io.casehub.blocks.conversation;

public record ThreadEntry(
        String entryId,
        Long messageId,
        io.casehub.qhorus.api.message.MessageType messageType,
        String role,
        int round,
        String entryType,
        String content) {}
