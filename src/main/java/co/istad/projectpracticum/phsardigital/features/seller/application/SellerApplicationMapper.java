package co.istad.projectpracticum.phsardigital.features.seller.application;

import co.istad.projectpracticum.phsardigital.features.seller.application.dto.ApplicationDocumentResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class SellerApplicationMapper {
    public ApplicationDocumentResponse toDocResponse(String uri, SellerApplicationDocument doc) {
        return new ApplicationDocumentResponse(
                doc.getUuid(),
                doc.getDocType(),
                doc.getObjectName(),
                uri);
    }
}
