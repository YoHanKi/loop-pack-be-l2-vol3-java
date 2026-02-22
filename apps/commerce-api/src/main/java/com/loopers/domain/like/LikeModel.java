package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.common.vo.RefProductId;
import com.loopers.infrastructure.jpa.converter.RefMemberIdConverter;
import com.loopers.infrastructure.jpa.converter.RefProductIdConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "likes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_likes_member_product", columnNames = {"ref_member_id", "ref_product_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeModel extends BaseEntity {

    @Convert(converter = RefMemberIdConverter.class)
    @Column(name = "ref_member_id", nullable = false)
    private RefMemberId refMemberId;

    @Convert(converter = RefProductIdConverter.class)
    @Column(name = "ref_product_id", nullable = false)
    private RefProductId refProductId;

    private LikeModel(Long refMemberId, Long refProductId) {
        this.refMemberId = new RefMemberId(refMemberId);
        this.refProductId = new RefProductId(refProductId);
    }

    public static LikeModel create(Long refMemberId, Long refProductId) {
        return new LikeModel(refMemberId, refProductId);
    }
}
