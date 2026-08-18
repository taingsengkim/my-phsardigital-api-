package co.istad.projectpracticum.phsardigital.features.address;

import co.istad.projectpracticum.phsardigital.features.address.dto.AddressRequest;
import co.istad.projectpracticum.phsardigital.features.address.dto.AddressResponse;
import co.istad.projectpracticum.phsardigital.features.address.dto.UpdateAddressRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public List<AddressResponse> myAddresses() {
        return addressService.getMyAddresses();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@Valid @RequestBody AddressRequest request) {
        return addressService.create(request);
    }

    @PatchMapping("/{id}")
    public AddressResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateAddressRequest request) {
        return addressService.update(id, request);
    }

    @PatchMapping("/{id}/default")
    public AddressResponse makeDefault(@PathVariable UUID id) {
        return addressService.makeDefault(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        addressService.delete(id);
    }
}
