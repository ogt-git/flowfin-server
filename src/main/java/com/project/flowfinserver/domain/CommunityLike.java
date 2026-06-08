package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "community_like",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_like",
                columnNames = {"user_id", "community_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    public static CommunityLike create(Long userId, Long communityId) {
        CommunityLike like = new CommunityLike();
        like.userId = userId;
        like.communityId = communityId;
        return like;
    }
}
