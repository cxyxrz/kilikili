package com.kilikili.web.entity;

import java.time.LocalDateTime;

public record ConversationSource(Long peerId, String peerName, String peerAvatar, String lastContent, LocalDateTime lastAt,
                                 Long unread) { }
