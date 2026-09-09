package co.istad.projectpracticum.phsardigital.features.purchases;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPurchaseSortTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("defaults to newest first")
    void defaultsToNewestFirst(String sortBy) {
        Sort sort = AdminPurchaseSort.parse(sortBy, null);

        assertThat(sort.getOrderFor("createdAt")).isNotNull();
        assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("maps the client's column name onto the entity attribute")
    void mapsTotalAmountOntoTotalPrice() {
        Sort sort = AdminPurchaseSort.parse("totalAmount", "asc");

        assertThat(sort.getOrderFor("totalPrice")).isNotNull();
        assertThat(sort.getOrderFor("totalPrice").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("totalAmount"))
                .as("the client's alias must not reach the repository")
                .isNull();
    }

    @Test
    @DisplayName("always breaks ties on the id, so paging cannot repeat or skip a row")
    void alwaysAddsATieBreak() {
        assertThat(AdminPurchaseSort.parse("status", "desc").getOrderFor("uuid")).isNotNull();
        assertThat(AdminPurchaseSort.parse(null, null).getOrderFor("uuid")).isNotNull();
    }

    @Test
    @DisplayName("rejects a column that is not on the allowlist")
    void rejectsUnknownColumn() {
        assertThatThrownBy(() -> AdminPurchaseSort.parse("sellerProfile.suspensionReason", "asc"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot sort orders by")
                .hasMessageContaining("createdAt");
    }

    @Test
    @DisplayName("rejects a direction that is neither asc nor desc")
    void rejectsUnknownDirection() {
        assertThatThrownBy(() -> AdminPurchaseSort.parse("createdAt", "sideways"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("asc");
    }

    @Test
    @DisplayName("accepts a direction in any case, since clients send both")
    void acceptsEitherCase() {
        assertThat(AdminPurchaseSort.parse("createdAt", "ASC").getOrderFor("createdAt")
                .getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(AdminPurchaseSort.parse("createdAt", "desc").getOrderFor("createdAt")
                .getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
