package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "community_like",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "community_id"})
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;
}