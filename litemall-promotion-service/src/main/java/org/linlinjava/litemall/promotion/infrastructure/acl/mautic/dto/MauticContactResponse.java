package org.linlinjava.litemall.promotion.infrastructure.acl.mautic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mautic response for a contact create/edit — the contact is wrapped under
 * {@code "contact"}. Only the id is consumed (to add the contact to a segment).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MauticContactResponse {

    private Contact contact;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contact {
        private Integer id;
    }
}
