package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.account.MintedDevice;
import com.aperture.apertureservice.domain.account.api.ConnectDevice;
import com.aperture.apertureservice.domain.account.api.ListDevices;
import com.aperture.apertureservice.domain.account.api.RevokeDevice;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/devices")
public class DevicesController {

    private final ConnectDevice connectDevice;
    private final ListDevices listDevices;
    private final RevokeDevice revokeDevice;

    public DevicesController(ConnectDevice connectDevice, ListDevices listDevices, RevokeDevice revokeDevice) {
        this.connectDevice = connectDevice;
        this.listDevices = listDevices;
        this.revokeDevice = revokeDevice;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceDtos.MintedResponse mint(Authentication auth,
                                          @Valid @RequestBody DeviceDtos.ConnectRequest body) {
        MintedDevice m = connectDevice.connect(MeController.userId(auth), body.name());
        return new DeviceDtos.MintedResponse(m.id(), m.name(), m.token());
    }

    @GetMapping
    public List<DeviceDtos.DeviceResponse> list(Authentication auth) {
        return listDevices.list(MeController.userId(auth)).stream()
                .map(DeviceDtos.DeviceResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication auth, @PathVariable UUID id) {
        revokeDevice.revoke(MeController.userId(auth), id);
    }
}
