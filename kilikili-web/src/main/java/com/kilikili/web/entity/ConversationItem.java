package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record ConversationItem(Long peerId, String peerName, String peerAvatar, String lastContent, LocalDateTime lastAt,
                               long unread) { }
