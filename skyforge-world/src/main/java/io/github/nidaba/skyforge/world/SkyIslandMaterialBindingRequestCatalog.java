package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0039 deterministic catalog of backend-neutral material-binding requests.
 *
 * <p>Planning-domain requests are materialized eagerly. CONTACT_TRANSITION requests are synthesized
 * deterministically from the anchored AUTH-0034 contact so every equivalent fallback key receives
 * the same request independent of sampling order.
 */
public final class SkyIslandMaterialBindingRequestCatalog {
    private final SkyIslandDescriptor descriptor;
    private final SkyIslandSemanticPaletteBindingPlan bindingPlan;
    private final Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialBindingRequest>
            plannedRequests;
    private final Map<Integer, SkyIslandLithologicContact> contactsById;
    private final Map<Integer, SkyIslandLithologicAssemblage> assemblagesById;

    private SkyIslandMaterialBindingRequestCatalog(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticPaletteBindingPlan bindingPlan) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.bindingPlan = Objects.requireNonNull(bindingPlan, "bindingPlan");
        if (!bindingPlan.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException(
                    "material-binding request catalog descriptor must match binding plan");
        }

        SkyIslandLithologicAssemblagePlan lithologicPlan =
                SkyIslandLithologicRealizationField.create(descriptor).assemblagePlan();
        this.assemblagesById = new HashMap<>();
        for (SkyIslandLithologicAssemblage assemblage : lithologicPlan.assemblages()) {
            SkyIslandLithologicAssemblage previous =
                    assemblagesById.put(assemblage.assemblageId(), assemblage);
            if (previous != null) {
                throw new IllegalStateException("duplicate AUTH-0034 assemblage id");
            }
        }
        this.contactsById = new HashMap<>();
        for (SkyIslandLithologicContact contact : lithologicPlan.contacts()) {
            SkyIslandLithologicContact previous =
                    contactsById.put(contact.contactId(), contact);
            if (previous != null) {
                throw new IllegalStateException("duplicate AUTH-0034 contact id");
            }
        }

        List<SkyIslandSemanticPaletteBindingDomain> domains =
                new ArrayList<>(bindingPlan.domains());
        domains.sort(Comparator
                .comparingInt((SkyIslandSemanticPaletteBindingDomain domain) ->
                        domain.key().role().ordinal())
                .thenComparingInt(domain -> domain.key().sourceChannel().ordinal())
                .thenComparingInt(domain -> domain.key().anchorId()));

        Map<SkyIslandSemanticPaletteBindingKey, SkyIslandMaterialBindingRequest> requests =
                new LinkedHashMap<>();
        for (SkyIslandSemanticPaletteBindingDomain domain : domains) {
            SkyIslandMaterialBindingRequest request = requestForDomain(domain);
            SkyIslandMaterialBindingRequest previous =
                    requests.put(request.bindingKey(), request);
            if (previous != null) {
                throw new IllegalStateException(
                        "duplicate planned AUTH-0039 material-binding request");
            }
        }
        this.plannedRequests = Map.copyOf(requests);
    }

    public static SkyIslandMaterialBindingRequestCatalog create(
            SkyIslandDescriptor descriptor) {
        return new SkyIslandMaterialBindingRequestCatalog(
                descriptor,
                SkyIslandSemanticPaletteBindingPlanner.plan(descriptor));
    }

    static SkyIslandMaterialBindingRequestCatalog create(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticPaletteBindingPlan bindingPlan) {
        return new SkyIslandMaterialBindingRequestCatalog(descriptor, bindingPlan);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandSemanticPaletteBindingPlan bindingPlan() {
        return bindingPlan;
    }

    public List<SkyIslandMaterialBindingRequest> plannedRequests() {
        return plannedRequests.values().stream()
                .sorted(Comparator
                        .comparingInt((SkyIslandMaterialBindingRequest request) ->
                                request.role().ordinal())
                        .thenComparingInt(request -> request.sourceChannel().ordinal())
                        .thenComparingInt(request -> request.bindingKey().anchorId()))
                .toList();
    }

    public SkyIslandMaterialBindingRequest request(
            SkyIslandSemanticPaletteBindingKey key) {
        Objects.requireNonNull(key, "key");
        if (!key.islandIdentity().equals(descriptor.identity())) {
            throw new IllegalArgumentException(
                    "material-binding request key belongs to a different island");
        }
        if (!SkyIslandMaterialBindingRequestPolicy.sourceChannelAllowed(
                key.role(), key.sourceChannel())) {
            throw new IllegalArgumentException(
                    "material-binding request key contains an invalid role/source channel");
        }

        SkyIslandMaterialBindingRequest planned = plannedRequests.get(key);
        if (planned != null) {
            return planned;
        }
        if (key.domainKind()
                != SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION) {
            throw new IllegalArgumentException(
                    "unknown non-contact AUTH-0038 binding key");
        }
        return contactRequest(key);
    }

    private SkyIslandMaterialBindingRequest requestForDomain(
            SkyIslandSemanticPaletteBindingDomain domain) {
        Map<Integer, SkyIslandMaterialBindingAssemblageContext> contextById =
                new HashMap<>();
        for (SkyIslandSemanticPaletteBindingCell cell : domain.cells()) {
            SkyIslandMaterialBindingAssemblageContext context =
                    new SkyIslandMaterialBindingAssemblageContext(
                            cell.assemblageId(), cell.assemblageKind());
            SkyIslandMaterialBindingAssemblageContext previous =
                    contextById.putIfAbsent(cell.assemblageId(), context);
            if (previous != null && previous.assemblageKind() != cell.assemblageKind()) {
                throw new IllegalStateException(
                        "AUTH-0038 domain contains inconsistent assemblage context");
            }
        }
        List<SkyIslandMaterialBindingAssemblageContext> contexts =
                contextById.values().stream()
                        .sorted(Comparator.comparingInt(
                                SkyIslandMaterialBindingAssemblageContext::assemblageId))
                        .toList();
        return createRequest(domain.key(), contexts, -1, null);
    }

    private SkyIslandMaterialBindingRequest contactRequest(
            SkyIslandSemanticPaletteBindingKey key) {
        SkyIslandLithologicContact contact = contactsById.get(key.anchorId());
        if (contact == null) {
            throw new IllegalArgumentException(
                    "unknown AUTH-0034 contact id for CONTACT_TRANSITION binding key");
        }
        List<SkyIslandMaterialBindingAssemblageContext> contexts = List.of(
                assemblageContext(contact.firstAssemblageId()),
                assemblageContext(contact.secondAssemblageId()));
        return createRequest(key, contexts, contact.contactId(), contact.kind());
    }

    private SkyIslandMaterialBindingAssemblageContext assemblageContext(int id) {
        SkyIslandLithologicAssemblage assemblage = assemblagesById.get(id);
        if (assemblage == null) {
            throw new IllegalStateException("unknown AUTH-0034 assemblage id " + id);
        }
        return new SkyIslandMaterialBindingAssemblageContext(
                assemblage.assemblageId(), assemblage.kind());
    }

    private static SkyIslandMaterialBindingRequest createRequest(
            SkyIslandSemanticPaletteBindingKey key,
            List<SkyIslandMaterialBindingAssemblageContext> contexts,
            int contactId,
            SkyIslandLithologicContactKind contactKind) {
        return new SkyIslandMaterialBindingRequest(
                key,
                SkyIslandMaterialBindingRequestPolicy.required(key.role()),
                SkyIslandMaterialBindingRequestPolicy.minimumEligibleSupport(key.role()),
                SkyIslandMaterialBindingRequestPolicy.minimumSecondaryHostRatio(key.role()),
                SkyIslandMaterialBindingRequestPolicy.maximumExpressionCeiling(key.role()),
                contexts,
                contactId,
                contactKind);
    }
}
