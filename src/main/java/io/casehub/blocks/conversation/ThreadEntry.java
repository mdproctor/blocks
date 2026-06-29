package io.casehub.blocks.conversation;

public record ThreadEntry(String entryId, String role, int round, String entryType, String content) {}
