package org.openmrs.module.labmanagement.api.model;

import org.openmrs.*;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;

@Entity(name = "labmanagement.Sample")
@Table(name = "labmgmt_sample")
@Indexed
public class Sample extends BaseChangeableOpenmrsData implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sample_id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_sample_id")
    private Sample parentSample;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sample_type_id", nullable = false)
    private Concept sampleType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "at_location_id")
    private Location atLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_type_id")
    private Concept containerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_by")
    private User collectedBy;

    @Column(name = "collection_date")
    private Date collectionDate;

    @Column(name = "container_count")
    private Integer containerCount;

    @Column(name = "accession_number")
    @GenericField
    private String accessionNumber;

    @Column(name = "provided_ref")
    @GenericField
    private String providedRef;

    @Column(name = "external_ref", length = 100)
    @GenericField
    private String externalRef;

    @Column(name = "referred_out")
    private Boolean referredOut;

    @JoinColumn(name = "current_sample_activity", nullable = true)
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    private SampleActivity currentSampleActivity;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private SampleStatus status;

    @Column(name = "storage_status",length = 50)
    @Enumerated(EnumType.STRING)
    private StorageStatus storageStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_request_id", nullable = false)
    private TestRequest testRequest;

    @Column(name = "volume", precision = 18, scale = 4)
    private BigDecimal volume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "volume_unit")
    private Concept volumeUnit;

    @OneToMany(mappedBy = "sample", cascade = CascadeType.ALL)
    private Set<TestRequestItemSample> testRequestItemSamples;

    @Column(name = "referral_out_origin", length = 50)
    @Enumerated(EnumType.STRING)
    private ReferralOutOrigin referralOutOrigin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_out_by")
    private User referralOutBy;

    @Column(name = "referral_out_date")
    private Date referralOutDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_to_facility_id")
    private ReferralLocation referralToFacility;

    @Column(name = "referral_to_facility_name")
    private String referralToFacilityName;

    @JoinColumn(name = "storage_unit_id", nullable = true)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private StorageUnit storageUnit;

    public TestRequest getTestRequest() {
        return testRequest;
    }

    public void setTestRequest(TestRequest testRequest) {
        this.testRequest = testRequest;
    }

    public Encounter getEncounter() {
        return encounter;
    }

    public void setEncounter(Encounter encounter) {
        this.encounter = encounter;
    }

    public SampleStatus getStatus() {
        return status;
    }

    public void setStatus(SampleStatus status) {
        this.status = status;
    }

    public SampleActivity getCurrentSampleActivity() {
        return currentSampleActivity;
    }

    public void setCurrentSampleActivity(SampleActivity currentSampleActivity) {
        this.currentSampleActivity = currentSampleActivity;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Sample getParentSample() {
        return parentSample;
    }

    public void setParentSample(Sample parentSample) {
        this.parentSample = parentSample;
    }

    public Concept getSampleType() {
        return sampleType;
    }

    public void setSampleType(Concept sampleType) {
        this.sampleType = sampleType;
    }

    public Location getAtLocation() {
        return atLocation;
    }

    public void setAtLocation(Location atLocation) {
        this.atLocation = atLocation;
    }

    public Concept getContainerType() {
        return containerType;
    }

    public void setContainerType(Concept containerType) {
        this.containerType = containerType;
    }

    public User getCollectedBy() {
        return collectedBy;
    }

    public void setCollectedBy(User collectedBy) {
        this.collectedBy = collectedBy;
    }

    public Date getCollectionDate() {
        return collectionDate;
    }

    public void setCollectionDate(Date collectionDate) {
        this.collectionDate = collectionDate;
    }

    public Integer getContainerCount() {
        return containerCount;
    }

    public void setContainerCount(Integer containerCount) {
        this.containerCount = containerCount;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getProvidedRef() {
        return providedRef;
    }

    public void setProvidedRef(String providedRef) {
        this.providedRef = providedRef;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public Boolean getReferredOut() {
        return referredOut;
    }

    public void setReferredOut(Boolean referredOut) {
        this.referredOut = referredOut;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public Concept getVolumeUnit() {
        return volumeUnit;
    }

    public void setVolumeUnit(Concept volumeUnit) {
        this.volumeUnit = volumeUnit;
    }

    public Set<TestRequestItemSample> getTestRequestItemSamples() {
        return testRequestItemSamples;
    }

    public void setTestRequestItemSamples(Set<TestRequestItemSample> testRequestItemSamples) {
        this.testRequestItemSamples = testRequestItemSamples;
    }

    public TestRequestItemSample addTestResult(TestRequestItemSample testRequestItemSample) {
        getTestRequestItemSamples().add(testRequestItemSample);
        testRequestItemSample.setSample(this);
        return testRequestItemSample;
    }

    public TestRequestItemSample removeTestResult(TestRequestItemSample testRequestItemSample) {
        getTestRequestItemSamples().remove(testRequestItemSample);
        testRequestItemSample.setSample(null);
        return testRequestItemSample;
    }

    public ReferralOutOrigin getReferralOutOrigin() {
        return referralOutOrigin;
    }

    public void setReferralOutOrigin(ReferralOutOrigin referralOutOrigin) {
        this.referralOutOrigin = referralOutOrigin;
    }

    public User getReferralOutBy() {
        return referralOutBy;
    }

    public void setReferralOutBy(User referralOutBy) {
        this.referralOutBy = referralOutBy;
    }

    public Date getReferralOutDate() {
        return referralOutDate;
    }

    public void setReferralOutDate(Date referralOutDate) {
        this.referralOutDate = referralOutDate;
    }

    public ReferralLocation getReferralToFacility() {
        return referralToFacility;
    }

    public void setReferralToFacility(ReferralLocation referralToFacility) {
        this.referralToFacility = referralToFacility;
    }

    public String getReferralToFacilityName() {
        return referralToFacilityName;
    }

    public void setReferralToFacilityName(String referralToFacilityName) {
        this.referralToFacilityName = referralToFacilityName;
    }

    public StorageUnit getStorageUnit() {
        return storageUnit;
    }

    public void setStorageUnit(StorageUnit storageUnit) {
        this.storageUnit = storageUnit;
    }

    public StorageStatus getStorageStatus() {
        return storageStatus;
    }

    public void setStorageStatus(StorageStatus storageStatus) {
        this.storageStatus = storageStatus;
    }
}
