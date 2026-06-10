package com.aperture.apertureservice.infrastructure.controller.web;

import com.aperture.apertureservice.domain.account.Email;
import com.aperture.apertureservice.domain.emergency.AlertConfiguration;
import com.aperture.apertureservice.domain.emergency.EmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.AddEmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.GetAlertConfiguration;
import com.aperture.apertureservice.domain.emergency.api.ListEmergencyContacts;
import com.aperture.apertureservice.domain.emergency.api.RemoveEmergencyContact;
import com.aperture.apertureservice.domain.emergency.api.UpdateAlertConfiguration;
import com.aperture.apertureservice.domain.emergency.api.UpdateEmergencyContact;
import com.aperture.apertureservice.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ContactsController.class, AlertConfigController.class})
@AutoConfigureMockMvc(addFilters = false)
class ContactsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AddEmergencyContact addContact;
    @MockitoBean UpdateEmergencyContact updateContact;
    @MockitoBean RemoveEmergencyContact removeContact;
    @MockitoBean ListEmergencyContacts listContacts;
    @MockitoBean GetAlertConfiguration getConfig;
    @MockitoBean UpdateAlertConfiguration updateConfig;

    private final UUID userId = UUID.randomUUID();

    private Authentication asUser() {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, UUID.randomUUID()), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void addListUpdateRemoveContacts() throws Exception {
        EmergencyContact c = new EmergencyContact(1L, userId, "Mom", new Email("mom@example.com"), null);
        when(addContact.add(userId, "Mom", "mom@example.com", null)).thenReturn(c);
        when(listContacts.list(userId)).thenReturn(List.of(c));
        when(updateContact.update(userId, 1L, "Mother", "mom@example.com", "hi"))
                .thenReturn(new EmergencyContact(1L, userId, "Mother", new Email("mom@example.com"), "hi"));

        mvc.perform(post("/api/v1/me/contacts").principal(asUser()).contentType("application/json")
                        .content("""
                                {"name":"Mom","email":"mom@example.com"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/v1/me/contacts").principal(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("mom@example.com"));
        mvc.perform(patch("/api/v1/me/contacts/1").principal(asUser()).contentType("application/json")
                        .content("""
                                {"name":"Mother","email":"mom@example.com","messageOverride":"hi"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mother"));
        mvc.perform(delete("/api/v1/me/contacts/1").principal(asUser()))
                .andExpect(status().isNoContent());
        verify(removeContact).remove(userId, 1L);
    }

    @Test
    void alertConfigGetAndPut() throws Exception {
        when(getConfig.get(userId)).thenReturn(AlertConfiguration.defaults(userId));
        when(updateConfig.update(userId, 0, "Go {{streamUrl}}"))
                .thenReturn(new AlertConfiguration(userId, 0, "Go {{streamUrl}}"));

        mvc.perform(get("/api/v1/me/alert-config").principal(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countdownDurationSeconds").value(30));
        mvc.perform(put("/api/v1/me/alert-config").principal(asUser()).contentType("application/json")
                        .content("""
                                {"countdownDurationSeconds":0,"messageTemplate":"Go {{streamUrl}}"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countdownDurationSeconds").value(0));
    }

    @Test
    void contactValidationRejectsBlankName() throws Exception {
        mvc.perform(post("/api/v1/me/contacts").principal(asUser()).contentType("application/json")
                        .content("""
                                {"name":"  ","email":"mom@example.com"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
