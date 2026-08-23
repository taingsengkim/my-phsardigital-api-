package co.istad.projectpracticum.phsardigital.features.listings;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.AttributeDataType;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttribute;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttribute;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.SetJoin;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingSpecificationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void publicBrowseRequiresStockAnActiveShopAndAnActiveCategory() {
        Root<Listing> listing = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<ListingStatus> status = mock(Path.class);
        Path<Integer> stock = mock(Path.class);
        Path<Object> seller = mock(Path.class);
        Path<Boolean> sellerActive = mock(Path.class);
        Path<Object> category = mock(Path.class);
        Path<Boolean> categoryActive = mock(Path.class);
        Path<Boolean> categoryDeleted = mock(Path.class);
        Predicate statusMatch = mock(Predicate.class);
        Predicate hasStock = mock(Predicate.class);
        Predicate shopAvailable = mock(Predicate.class);
        Predicate categoryAvailable = mock(Predicate.class);
        Predicate categoryPresent = mock(Predicate.class);
        Predicate result = mock(Predicate.class);

        when(listing.<ListingStatus>get("status")).thenReturn(status);
        when(listing.<Integer>get("stockQty")).thenReturn(stock);
        when(listing.<Object>get("sellerProfile")).thenReturn(seller);
        when(seller.<Boolean>get("isActive")).thenReturn(sellerActive);
        when(listing.<Object>get("category")).thenReturn(category);
        when(category.<Boolean>get("isActive")).thenReturn(categoryActive);
        when(category.<Boolean>get("isDeleted")).thenReturn(categoryDeleted);
        when(builder.equal(status, ListingStatus.ACTIVE)).thenReturn(statusMatch);
        when(builder.greaterThan(stock, 0)).thenReturn(hasStock);
        when(builder.isTrue(sellerActive)).thenReturn(shopAvailable);
        when(builder.isTrue(categoryActive)).thenReturn(categoryAvailable);
        when(builder.isFalse(categoryDeleted)).thenReturn(categoryPresent);
        when(builder.and(statusMatch, hasStock, shopAvailable, categoryAvailable, categoryPresent))
                .thenReturn(result);

        assertThat(ListingSpecifications.publiclyBrowsable()
                .toPredicate(listing, query, builder)).isSameAs(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void attributeFacetMatchesEitherTheScalarOrAnIndividualMultiSelectValue() {
        Root<Listing> listing = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Subquery<UUID> subquery = mock(Subquery.class);
        Root<ListingAttribute> attribute = mock(Root.class);
        SetJoin<ListingAttribute, String> selectedValue = mock(SetJoin.class);
        Join<ListingAttribute, CategoryAttribute> definition = mock(Join.class);

        Path<Object> attributeListing = mock(Path.class);
        Path<UUID> attributeListingUuid = mock(Path.class);
        Path<UUID> listingUuid = mock(Path.class);
        Path<String> key = mock(Path.class);
        Path<String> value = mock(Path.class);
        Path<AttributeDataType> dataType = mock(Path.class);
        Expression<String> lowerKey = mock(Expression.class);
        Expression<String> lowerValue = mock(Expression.class);
        Expression<String> lowerSelectedValue = mock(Expression.class);
        Expression<String> commaSpace = mock(Expression.class);
        Expression<String> comma = mock(Expression.class);
        Expression<String> compactValue = mock(Expression.class);
        Expression<String> leadingComma = mock(Expression.class);
        Expression<String> paddedValue = mock(Expression.class);
        Predicate sameListing = mock(Predicate.class);
        Predicate sameKey = mock(Predicate.class);
        Predicate scalarMatch = mock(Predicate.class);
        Predicate selectedMatch = mock(Predicate.class);
        Predicate multiSelectType = mock(Predicate.class);
        Predicate legacyTokenMatch = mock(Predicate.class);
        Predicate anyLegacyToken = mock(Predicate.class);
        Predicate legacyMultiSelectMatch = mock(Predicate.class);
        Predicate eitherValue = mock(Predicate.class);
        Predicate allConditions = mock(Predicate.class);
        Predicate exists = mock(Predicate.class);

        when(query.subquery(UUID.class)).thenReturn(subquery);
        when(subquery.from(ListingAttribute.class)).thenReturn(attribute);
        when(attribute.<ListingAttribute, String>joinSet("selectedValues", JoinType.LEFT))
                .thenReturn(selectedValue);
        when(attribute.<ListingAttribute, CategoryAttribute>join("definition", JoinType.LEFT))
                .thenReturn(definition);
        when(attribute.<Object>get("listing")).thenReturn(attributeListing);
        when(attributeListing.<UUID>get("uuid")).thenReturn(attributeListingUuid);
        when(listing.<UUID>get("uuid")).thenReturn(listingUuid);
        when(attribute.<String>get("key")).thenReturn(key);
        when(attribute.<String>get("value")).thenReturn(value);
        when(definition.<AttributeDataType>get("dataType")).thenReturn(dataType);
        when(builder.lower(key)).thenReturn(lowerKey);
        when(builder.lower(value)).thenReturn(lowerValue);
        when(builder.lower(selectedValue)).thenReturn(lowerSelectedValue);
        when(builder.literal(", ")).thenReturn(commaSpace);
        when(builder.literal(",")).thenReturn(comma);
        when(builder.function("replace", String.class, lowerValue, commaSpace, comma))
                .thenReturn(compactValue);
        when(builder.concat(",", compactValue)).thenReturn(leadingComma);
        when(builder.concat(leadingComma, ",")).thenReturn(paddedValue);
        when(builder.equal(attributeListingUuid, listingUuid)).thenReturn(sameListing);
        when(builder.equal(lowerKey, "colours")).thenReturn(sameKey);
        when(builder.equal(dataType, AttributeDataType.MULTI_SELECT)).thenReturn(multiSelectType);
        when(lowerValue.in(List.of("black"))).thenReturn(scalarMatch);
        when(lowerSelectedValue.in(List.of("black"))).thenReturn(selectedMatch);
        when(builder.like(paddedValue, "%,black,%", '\\')).thenReturn(legacyTokenMatch);
        when(builder.or(legacyTokenMatch)).thenReturn(anyLegacyToken);
        when(builder.and(multiSelectType, anyLegacyToken)).thenReturn(legacyMultiSelectMatch);
        when(builder.or(scalarMatch, selectedMatch, legacyMultiSelectMatch)).thenReturn(eitherValue);
        when(builder.and(sameListing, sameKey, eitherValue)).thenReturn(allConditions);
        when(subquery.select(attributeListingUuid)).thenReturn(subquery);
        when(subquery.where(allConditions)).thenReturn(subquery);
        when(builder.exists(subquery)).thenReturn(exists);

        Specification<Listing> specification =
                ListingSpecifications.hasAttribute("Colours", List.of("BLACK"));

        assertThat(specification.toPredicate(listing, query, builder)).isSameAs(exists);
        verify(attribute).<ListingAttribute, String>joinSet("selectedValues", JoinType.LEFT);
        verify(attribute).<ListingAttribute, CategoryAttribute>join("definition", JoinType.LEFT);
        verify(builder).or(scalarMatch, selectedMatch, legacyMultiSelectMatch);
        verify(builder).like(paddedValue, "%,black,%", '\\');
    }
}
