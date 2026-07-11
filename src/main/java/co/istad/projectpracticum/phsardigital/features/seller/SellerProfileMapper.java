package co.istad.projectpracticum.phsardigital.features.seller;


import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SellerProfileMapper {

    @Mapping(target = "id", source = "sellerId")
    SellerProfileResponse toResponse(SellerProfile sellerProfile);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(SellerProfileUpdateRequest request, @MappingTarget SellerProfile sellerProfile);
}