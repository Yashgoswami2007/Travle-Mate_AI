-- Migration: 04-chat-system.sql
-- Add schema for Chat MVP

-- 1. Users
CREATE TABLE IF NOT EXISTS chat_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username TEXT UNIQUE NOT NULL CHECK (char_length(username) >= 3),
    display_name TEXT NOT NULL,
    avatar_path TEXT,
    bio TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Case insensitive search index
CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_users_username_lower ON chat_users (lower(username));

-- 2. Conversations
CREATE TABLE IF NOT EXISTS chat_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type TEXT NOT NULL CHECK (type IN ('DIRECT', 'GROUP')),
    title TEXT,
    description TEXT,
    image_path TEXT,
    created_by UUID REFERENCES chat_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ
);

-- 3. Conversation Members
CREATE TABLE IF NOT EXISTS chat_members (
    conversation_id UUID REFERENCES chat_conversations(id) ON DELETE CASCADE,
    user_id UUID REFERENCES chat_users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('MEMBER', 'ADMIN')) DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at TIMESTAMPTZ,
    last_read_message_id UUID,
    PRIMARY KEY (conversation_id, user_id)
);

-- 4. Messages
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES chat_conversations(id) ON DELETE CASCADE NOT NULL,
    sender_id UUID REFERENCES chat_users(id) NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('TEXT', 'IMAGE', 'VIDEO', 'AUDIO')),
    text_content TEXT,
    reply_to_message_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    client_message_id TEXT
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conv_id ON chat_messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at ON chat_messages(created_at);

-- 5. Message Attachments
CREATE TABLE IF NOT EXISTS chat_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID REFERENCES chat_messages(id) ON DELETE CASCADE NOT NULL,
    storage_path TEXT NOT NULL,
    mime_type TEXT,
    size_bytes BIGINT,
    duration_ms INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. Message Reactions
CREATE TABLE IF NOT EXISTS chat_reactions (
    message_id UUID REFERENCES chat_messages(id) ON DELETE CASCADE NOT NULL,
    user_id UUID REFERENCES chat_users(id) ON DELETE CASCADE NOT NULL,
    emoji TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (message_id, user_id, emoji)
);
