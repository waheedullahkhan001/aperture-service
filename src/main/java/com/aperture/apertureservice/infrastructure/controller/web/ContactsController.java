package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.emergency.api.AddEmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.ListEmergencyContacts;
import com.aperture.apertureservice.domain.emergency.api.RemoveEmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.UpdateEmergencyContact;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/contacts")
public class ContactsController {

    private final AddEmergencyContact addContact;
    private final UpdateEmergencyContact updateContact;
    private final RemoveEmergencyContact removeContact;
    private final ListEmergencyContacts listContacts;

    public ContactsController(AddEmergencyContact addContact, UpdateEmergencyContact updateContact,
                              RemoveEmergencyContact removeContact, ListEmergencyContacts listContacts) {
        this.addContact = addContact;
        this.updateContact = updateContact;
        this.removeContact = removeContact;
        this.listContacts = listContacts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactDtos.ContactResponse add(Authentication auth,
                                           @Valid @RequestBody ContactDtos.ContactRequest body) {
        return ContactDtos.ContactResponse.from(addContact.add(MeController.userId(auth),
                body.name(), body.email(), body.messageOverride()));
    }

    @GetMapping
    public List<ContactDtos.ContactResponse> list(Authentication auth) {
        return listContacts.list(MeController.userId(auth)).stream()
                .map(ContactDtos.ContactResponse::from).toList();
    }

    @PatchMapping("/{id}")
    public ContactDtos.ContactResponse update(Authentication auth, @PathVariable Long id,
                                              @Valid @RequestBody ContactDtos.ContactRequest body) {
        return ContactDtos.ContactResponse.from(updateContact.update(MeController.userId(auth), id,
                body.name(), body.email(), body.messageOverride()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable Long id) {
        removeContact.remove(MeController.userId(auth), id);
    }
}
