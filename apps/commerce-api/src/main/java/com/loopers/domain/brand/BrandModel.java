package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.vo.BrandId;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.infrastructure.jpa.converter.BrandIdConverter;
import com.loopers.infrastructure.jpa.converter.BrandNameConverter;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "brands")
@Getter
public class BrandModel extends BaseEntity {

    @Convert(converter = BrandIdConverter.class)
    @Column(name = "brand_id", nullable = false, unique = true, length = 10)
    private BrandId brandId;

    @Convert(converter = BrandNameConverter.class)
    @Column(name = "brand_name", nullable = false, length = 50)
    private BrandName brandName;

    protected BrandModel() {}

    private BrandModel(String brandId, String brandName) {
        this.brandId = new BrandId(brandId);
        this.brandName = new BrandName(brandName);
    }

    public static BrandModel create(String brandId, String brandName) {
        return new BrandModel(brandId, brandName);
    }

    public void markAsDeleted() {
        delete();
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
