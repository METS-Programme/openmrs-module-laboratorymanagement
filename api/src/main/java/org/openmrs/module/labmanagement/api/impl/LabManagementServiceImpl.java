/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * <p>
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.labmanagement.api.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.*;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.*;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.labmanagement.LabLocationTags;
import org.openmrs.module.labmanagement.api.LabManagementException;
import org.openmrs.module.labmanagement.api.LabManagementService;
import org.openmrs.module.labmanagement.api.Privileges;
import org.openmrs.module.labmanagement.api.dao.LabManagementDao;
import org.openmrs.module.labmanagement.api.dto.*;
import org.openmrs.module.labmanagement.api.jobs.AsyncTasksBatchJob;
import org.openmrs.module.labmanagement.api.jobs.TestConfigImportJob;
import org.openmrs.module.labmanagement.api.model.*;
import org.openmrs.module.labmanagement.api.dto.TestApprovalDTO;
import org.openmrs.module.labmanagement.api.reporting.GenericObject;
import org.openmrs.module.labmanagement.api.reporting.Report;
import org.openmrs.module.labmanagement.api.utils.GlobalProperties;
import org.openmrs.module.labmanagement.api.utils.MapUtil;
import org.openmrs.module.labmanagement.api.utils.Pair;
import org.openmrs.notification.Alert;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.util.LocaleUtility;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.transaction.annotation.Transactional;
import org.openmrs.module.patientqueueing.api.PatientQueueingService;
import org.openmrs.module.patientqueueing.model.PatientQueue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("deprecation")
public class LabManagementServiceImpl extends BaseOpenmrsService implements LabManagementService {

    LabManagementDao dao;

    private final Log log = LogFactory.getLog(this.getClass());
    private static Object WORKSHEET_LOCK = new Object();

    public LabManagementServiceImpl() {

    }

    /**
     * Injected in moduleApplicationContext.xml
     */
    public void setDao(LabManagementDao dao) {
        this.dao = dao;
    }

    private void invalidRequest(String messageKey) {
        throw new LabManagementException(Context.getMessageSourceService().getMessage(messageKey));
    }

    private void invalidRequest(String message, String... args) {
        final MessageSourceService messageService = Context.getMessageSourceService();
        throw new LabManagementException(String.format(messageService.getMessage(message), Arrays.stream( args).map(messageService::getMessage).toArray()));
    }

    public Result<TestConfigDTO> findTestConfigurations(TestConfigSearchFilter filter) {
        if(!StringUtils.isBlank(filter.getSearchText())){
            List<Locale> locales = new ArrayList<Locale>(LocaleUtility.getLocalesInOrder());
            Integer maxIntermediateResult = GlobalProperties.getTestSearchMaxConceptIntermediateResult();
            List<Concept> searchResults = Context.getConceptService().getConcepts(filter.getSearchText(), locales, true, null, null, null, null, null, 0, maxIntermediateResult * 2)
                    .stream()
                    .map(ConceptSearchResult::getConcept)
                    .collect(Collectors.toList());
            if (searchResults.isEmpty())
                return new Result<>(new ArrayList<>(), 0);
            else {
                    filter.setTestOrGroupIds(searchResults.stream().map(Concept::getConceptId).collect(Collectors.toList()));
            }
        }
        Result<TestConfigDTO> result =  dao.findTestConfigurations(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        for (TestConfigDTO testConfigDTO : result.getData()) {
            if (testConfigDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testConfigDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testConfigDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    testConfigDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testConfigDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testConfigDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testConfigDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testConfigDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }
        return result;
    }

    public TestConfig getTestConfigurationById(Integer id) {
        return dao.getTestConfigById(id);
    }

    public TestConfig getTestConfigurationByUuid(String uuid) {
        return dao.getTestConfigByUuid(uuid);
    }

    public TestConfig saveTestConfig(TestConfig testConfig){
        return dao.saveTestConfig(testConfig);
    }
    public TestConfig saveTestConfig(TestConfigDTO testConfigDTO) {
        TestConfig testConfig;
        if (testConfigDTO.getUuid() == null) {
            testConfig = new TestConfig();
            testConfig.setCreator(Context.getAuthenticatedUser());
            testConfig.setDateCreated(new Date());

            if (!StringUtils.isBlank(testConfigDTO.getTestUuid())) {
                Concept concept = Context.getConceptService().getConceptByUuid(testConfigDTO.getTestUuid());
                if (concept != null) {
                    TestConfigSearchFilter filter = new TestConfigSearchFilter();
                    filter.setTestUuid(testConfigDTO.getTestUuid());
                    filter.setLimit(1);
                    Result<TestConfigDTO> testConfigResult = findTestConfigurations(filter);
                    if (!testConfigResult.getData().isEmpty()) {
                        invalidRequest("labmanagement.testconfig.conceptexists");
                    }
                    testConfig.setTest(concept);
                } else {
                    invalidRequest("labmanagement.notexists", "testUuid");
                }
            } else {
                invalidRequest("labmanagement.conceptrequired", "testUuid");
            }
        } else {
            testConfig = getTestConfigurationByUuid(testConfigDTO.getUuid());
            if (testConfig == null) {
                invalidRequest("labmanagement.thingnotexists", Context.getMessageSourceService().getMessage("labmanagement.testconfig"));
            }

            testConfig.setChangedBy(Context.getAuthenticatedUser());
            testConfig.setDateChanged(new Date());
        }

        if (!StringUtils.isBlank(testConfigDTO.getTestGroupUuid())) {
            Concept concept = Context.getConceptService().getConceptByUuid(testConfigDTO.getTestGroupUuid());
            if (concept != null) {
                testConfig.setTestGroup(concept);
            } else {
                invalidRequest("labmanagement.conceptnotexist", Context.getMessageSourceService().getMessage("labmanagement.testgroup"));
            }
        } else {
            testConfig.setTestGroup(null);
        }

        if (!StringUtils.isBlank(testConfigDTO.getApprovalFlowUuid())) {
            ApprovalFlow approvalFlow = getApprovalFlowByUuid(testConfigDTO.getApprovalFlowUuid());
            if (approvalFlow != null) {
                testConfig.setApprovalFlow(approvalFlow);
            } else {
                invalidRequest("labmanagement.notexists", Context.getMessageSourceService().getMessage("labmanagement.approvalflow"));
            }
        } else {
            testConfig.setApprovalFlow(null);
        }

        if (testConfigDTO.getRequireApproval() == null) {
            invalidRequest("labmanagement.fieldrequired", "Require approval");
        }
        else if (testConfigDTO.getRequireApproval().equals(Boolean.TRUE) && testConfig.getApprovalFlow() == null) {
            invalidRequest("labmanagement.fieldrequiredif",
                    Context.getMessageSourceService().getMessage("labmanagement.approvalflow"),
                    Context.getMessageSourceService().getMessage("labmanagement.requireapproval"));
        }else if(testConfigDTO.getRequireApproval().equals(Boolean.FALSE)){
            testConfig.setApprovalFlow(null);
        }

        testConfig.setEnabled(testConfigDTO.getEnabled());
        testConfig.setRequireApproval(testConfigDTO.getRequireApproval());

        dao.saveTestConfig(testConfig);

        return testConfig;
    }

    public Result<ApprovalFlowDTO> findApprovalFlows(ApprovalFlowSearchFilter filter) {
        Result<ApprovalFlowDTO> result = dao.findApprovalFlows(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        for (ApprovalFlowDTO approvalFlowDTO : result.getData()) {
            if (approvalFlowDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(approvalFlowDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    approvalFlowDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    approvalFlowDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (approvalFlowDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(approvalFlowDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    approvalFlowDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    approvalFlowDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }
        return result;
    }

    public ApprovalFlow getApprovalFlowById(Integer id) {
        return dao.getApprovalFlowById(id);
    }

    public ApprovalFlow getApprovalFlowByUuid(String uuid) {
        return dao.getApprovalFlowByUuid(uuid);
    }

    public ApprovalFlow saveApprovalFlow(ApprovalFlowDTO approvalFlowDTO) {
        ApprovalFlow approvalFlow;
        boolean isNew;
        if (approvalFlowDTO.getUuid() == null) {
            isNew = true;
            approvalFlow = new ApprovalFlow();
            approvalFlow.setCreator(Context.getAuthenticatedUser());
            approvalFlow.setDateCreated(new Date());
        } else {
            isNew = false;
            approvalFlow = getApprovalFlowByUuid(approvalFlowDTO.getUuid());
            if (approvalFlow == null) {
                invalidRequest("labmanagement.thingnotexists", Context.getMessageSourceService().getMessage("labmanagement.approvalflow"));
            }

            approvalFlow.setChangedBy(Context.getAuthenticatedUser());
            approvalFlow.setDateChanged(new Date());
        }

        if(StringUtils.isBlank(approvalFlowDTO.getName())){
            invalidRequest("labmanagement.fieldrequired", "Name");
        }else{
            ApprovalFlowSearchFilter searchFilter = new ApprovalFlowSearchFilter();
            searchFilter.setNameOrSystemName(approvalFlowDTO.getName());
            if(findApprovalFlows(searchFilter).getData().stream().anyMatch(p-> isNew || !p.getId().equals(approvalFlow.getId()))){
                invalidRequest("labmanagement.approvalflow.nameexists");
            }
        }

        if(StringUtils.isBlank(approvalFlowDTO.getSystemName())){
            invalidRequest("labmanagement.fieldrequired", "System Name");
        }else{
            ApprovalFlowSearchFilter searchFilter = new ApprovalFlowSearchFilter();
            searchFilter.setNameOrSystemName(approvalFlowDTO.getSystemName());
            if(findApprovalFlows(searchFilter).getData().stream().anyMatch(p-> isNew || !p.getId().equals(approvalFlow.getId()))){
                invalidRequest("labmanagement.approvalflow.systemnameexists");
            }
        }

        if(StringUtils.isBlank(approvalFlowDTO.getLevelOneUuid())){
            invalidRequest("labmanagement.approvallevelonerequired", "Name");
        }else{
            ApprovalConfig approvalConfig = dao.getApprovalConfigByUuid(approvalFlowDTO.getLevelOneUuid());
            if(approvalConfig == null){
                invalidRequest("labmanagement.approvallevelconfiginvalid", "one");
            }
            approvalFlow.setLevelOne(approvalConfig);
            approvalFlow.setLevelOneAllowOwner(approvalFlowDTO.getLevelOneAllowOwner() != null && approvalFlowDTO.getLevelOneAllowOwner());
        }

        List<Object[]> levelOptions = new ArrayList<>();
        if(StringUtils.isNotBlank(approvalFlowDTO.getLevelTwoUuid())){
            ApprovalConfig approvalConfig = dao.getApprovalConfigByUuid(approvalFlowDTO.getLevelTwoUuid());
            if(approvalConfig == null){
                invalidRequest("labmanagement.approvallevelconfiginvalid", "two");
            }
            levelOptions.add(new Object[]{approvalConfig,
                    approvalFlowDTO.getLevelTwoAllowOwner() != null && approvalFlowDTO.getLevelTwoAllowOwner(),
                    approvalFlowDTO.getLevelTwoAllowPrevious() != null && approvalFlowDTO.getLevelTwoAllowPrevious()});
        }

        if(StringUtils.isNotBlank(approvalFlowDTO.getLevelThreeUuid())){
            ApprovalConfig approvalConfig = dao.getApprovalConfigByUuid(approvalFlowDTO.getLevelThreeUuid());
            if(approvalConfig == null){
                invalidRequest("labmanagement.approvallevelconfiginvalid", "three");
            }

            levelOptions.add(new Object[]{approvalConfig,
                    approvalFlowDTO.getLevelThreeAllowOwner() != null && approvalFlowDTO.getLevelThreeAllowOwner(),
                    approvalFlowDTO.getLevelThreeAllowPrevious() != null && approvalFlowDTO.getLevelThreeAllowPrevious()});
        }

        if(StringUtils.isNotBlank(approvalFlowDTO.getLevelFourUuid())){
            ApprovalConfig approvalConfig = dao.getApprovalConfigByUuid(approvalFlowDTO.getLevelFourUuid());
            if(approvalConfig == null){
                invalidRequest("labmanagement.approvallevelconfiginvalid", "four");
            }
            levelOptions.add(new Object[]{approvalConfig,
                    approvalFlowDTO.getLevelFourAllowOwner() != null && approvalFlowDTO.getLevelFourAllowOwner(),
                    approvalFlowDTO.getLevelFourAllowPrevious() != null && approvalFlowDTO.getLevelFourAllowPrevious()});

        }

        if(!levelOptions.isEmpty()){
            Object[] levelOptionValues = levelOptions.get(0);
            approvalFlow.setLevelTwo((ApprovalConfig) levelOptionValues[0]);
            approvalFlow.setLevelTwoAllowOwner((Boolean) levelOptionValues[1]);
            approvalFlow.setLevelTwoAllowPrevious((Boolean) levelOptionValues[2]);
        }else{
            approvalFlow.setLevelTwo(null);
            approvalFlow.setLevelTwoAllowOwner(true);
            approvalFlow.setLevelTwoAllowPrevious(true);
        }

        if(levelOptions.size() > 1){
            Object[] levelOptionValues = levelOptions.get(1);
            approvalFlow.setLevelThree((ApprovalConfig) levelOptionValues[0]);
            approvalFlow.setLevelThreeAllowOwner((Boolean) levelOptionValues[1]);
            approvalFlow.setLevelThreeAllowPrevious((Boolean) levelOptionValues[2]);
        }else{
            approvalFlow.setLevelThree(null);
            approvalFlow.setLevelThreeAllowOwner(true);
            approvalFlow.setLevelThreeAllowPrevious(true);
        }

        if(levelOptions.size() > 2){
            Object[] levelOptionValues = levelOptions.get(2);
            approvalFlow.setLevelFour((ApprovalConfig) levelOptionValues[0]);
            approvalFlow.setLevelFourAllowOwner((Boolean) levelOptionValues[1]);
            approvalFlow.setLevelFourAllowPrevious((Boolean) levelOptionValues[2]);
        }else{
            approvalFlow.setLevelFour(null);
            approvalFlow.setLevelFourAllowOwner(true);
            approvalFlow.setLevelFourAllowPrevious(true);
        }

        approvalFlow.setName(approvalFlowDTO.getName());
        approvalFlow.setSystemName(approvalFlowDTO.getSystemName());

        dao.saveApprovalFlow(approvalFlow);

        return approvalFlow;
    }

    public void deleteApprovalFlow(String uuid){
        ApprovalFlow approvalFlow = dao.getApprovalFlowByUuid(uuid);
        if(approvalFlow == null || approvalFlow.getVoided()) return;

        TestConfigSearchFilter searchFilter=new TestConfigSearchFilter();
        searchFilter.setVoided(false);
        searchFilter.setApprovalFlowId(approvalFlow.getId());
        Result<?>  result = findTestConfigurations(searchFilter);
        if(!result.getData().isEmpty()){
            invalidRequest("labmanagement.approvalflow.inuse");
        }

        approvalFlow.setVoided(true);
        dao.saveApprovalFlow(approvalFlow);
    }

    public ImportResult importTestConfigurations(Path file, boolean hasHeader) {
        TestConfigImportJob importJob = new TestConfigImportJob(file, hasHeader);
        importJob.execute();
        return (ImportResult) importJob.getResult();
    }

    public List<Concept> getConcepts(Collection<Integer> conceptIds) {
        return dao.getConcepts(conceptIds);
    }

    public List<ApprovalFlow> getApprovalFlowsBySystemName(List<String> approvalFlowSystemNames){
        return dao.getApprovalFlowsBySystemName(approvalFlowSystemNames);
    }

    public List<TestConfig> getTestConfigsByIds(Collection<Integer> testConfigIds){
        return dao.getTestConfigsByIds(testConfigIds);
    }

    public ApprovalFlow saveApprovalFlow(ApprovalFlow approvalFlow){
        return dao.saveApprovalFlow(approvalFlow);
    }

    public Result<ApprovalConfigDTO> findApprovalConfigurations(ApprovalConfigSearchFilter filter){
        Result<ApprovalConfigDTO> result = dao.findApprovalConfigurations(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        for (ApprovalConfigDTO approvalConfigDTO : result.getData()) {
            if (approvalConfigDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(approvalConfigDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    approvalConfigDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    approvalConfigDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (approvalConfigDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(approvalConfigDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    approvalConfigDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    approvalConfigDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }
        return result;
    }

    public ApprovalConfig getApprovalConfigById(Integer id) {
        return dao.getApprovalConfigById(id);
    }

    public ApprovalConfig getApprovalConfigByUuid(String uuid) {
        return dao.getApprovalConfigByUuid(uuid);
    }

    public ApprovalConfig saveApprovalConfig(ApprovalConfigDTO approvalConfigDTO) {
        ApprovalConfig approvalConfig;
        boolean isNew;
        if (approvalConfigDTO.getUuid() == null) {
            isNew = true;
            approvalConfig = new ApprovalConfig();
            approvalConfig.setCreator(Context.getAuthenticatedUser());
            approvalConfig.setDateCreated(new Date());
        } else {
            isNew = false;
            approvalConfig = getApprovalConfigByUuid(approvalConfigDTO.getUuid());
            if (approvalConfig == null) {
                invalidRequest("labmanagement.thingnotexists", Context.getMessageSourceService().getMessage("labmanagement.approvalconfig"));
            }
            approvalConfig.setChangedBy(Context.getAuthenticatedUser());
            approvalConfig.setDateChanged(new Date());
        }

        if(StringUtils.isBlank(approvalConfigDTO.getApprovalTitle())){
            invalidRequest("labmanagement.fieldrequired", "Approval Title");
        }else{
            ApprovalConfigSearchFilter searchFilter = new ApprovalConfigSearchFilter();
            searchFilter.setApprovalTitle(approvalConfigDTO.getApprovalTitle());
            if(findApprovalConfigurations(searchFilter).getData().stream().anyMatch(p-> isNew || !p.getId().equals(approvalConfig.getId()))){
                invalidRequest("labmanagement.approvalconfig.nameexists");
            }
            approvalConfig.setApprovalTitle(approvalConfigDTO.getApprovalTitle());
        }

        if(StringUtils.isBlank(approvalConfigDTO.getPrivilege())){
            invalidRequest("labmanagement.fieldrequired", "Privilege");
        }else{
            Privilege privilege = Context.getUserService().getPrivilege(approvalConfigDTO.getPrivilege());
            if(privilege == null){
                invalidRequest("labmanagement.thingnotexists", "Privilege");
            }
            approvalConfig.setPrivilege(approvalConfigDTO.getPrivilege());
        }

        if(StringUtils.isBlank(approvalConfigDTO.getPendingStatus())){
            invalidRequest("labmanagement.fieldrequired", "Pending status");
        }else{
            approvalConfig.setPendingStatus(approvalConfigDTO.getPendingStatus());
        }

        if(StringUtils.isBlank(approvalConfigDTO.getReturnedStatus())){
            invalidRequest("labmanagement.fieldrequired", "Returned status");
        }else{
            approvalConfig.setReturnedStatus(approvalConfigDTO.getReturnedStatus());
        }

        if(StringUtils.isBlank(approvalConfigDTO.getRejectedStatus())){
            invalidRequest("labmanagement.fieldrequired", "Rejected status");
        }else{
            approvalConfig.setRejectedStatus(approvalConfigDTO.getRejectedStatus());
        }

        if(StringUtils.isBlank(approvalConfigDTO.getApprovedStatus())){
            invalidRequest("labmanagement.fieldrequired", "Approved status");
        }else{
            approvalConfig.setApprovedStatus(approvalConfigDTO.getApprovedStatus());
        }

        dao.saveApprovalConfig(approvalConfig);
        return approvalConfig;
    }

    public void deleteApprovalConfig(String uuid){
        ApprovalConfig approvalConfig = dao.getApprovalConfigByUuid(uuid);
        if(approvalConfig == null || approvalConfig.getVoided()) return;

        ApprovalFlowSearchFilter searchFilter=new ApprovalFlowSearchFilter();
        searchFilter.setVoided(false);
        searchFilter.setApprovalConfigId(approvalConfig.getId());
        Result<?>  result = findApprovalFlows(searchFilter);
        if(!result.getData().isEmpty()){
            invalidRequest("labmanagement.approvalconfig.inuseflow");
        }

        if(dao.checkApprovalConfigUsage(approvalConfig)){
            invalidRequest("labmanagement.approvalconfig.inuseapprovals");
        }
        approvalConfig.setVoided(true);
        dao.saveApprovalConfig(approvalConfig);
    }

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTREQUESTS_MUTATE)
    public TestRequest saveTestRequest(TestRequestDTO testRequestDTO){
        if (testRequestDTO.getUuid() != null) {
            invalidRequest("labmanagement.testrequest.updatesnoallowed");
        }

        if(testRequestDTO.getRequestDate() == null){
            invalidRequest("labmanagement.fieldrequired", "Request date");
        }

        if(testRequestDTO.getRequestDate().after(new Date())){
            invalidRequest("labmanagement.futuredatenotallowed", "Request date");
        }

        if(testRequestDTO.getUrgency() == null){
            invalidRequest("labmanagement.fieldrequired", "Urgency");
        }

        if(!Arrays.stream(Order.Urgency.values()).anyMatch(p->p.equals(testRequestDTO.getUrgency()))){
            invalidRequest("labmanagement.thingnotexists",  "Urgency");
        }

        if(StringUtils.isBlank(testRequestDTO.getAtLocationUuid())){
            invalidRequest("labmanagement.fieldrequired", "Current user location");
        }

        if(StringUtils.isNotBlank(testRequestDTO.getReferralInExternalRef()) && testRequestDTO.getReferralInExternalRef().length() > 50){
            invalidRequest("labmanagement.thingnotexceeds", "Referral Reference Number", "50");
        }

        if(StringUtils.isNotBlank(testRequestDTO.getClinicalNote()) && testRequestDTO.getClinicalNote().length() > 500){
            invalidRequest("labmanagement.thingnotexceeds", "Clinical notes", "500");
        }

        if(StringUtils.isNotBlank(testRequestDTO.getRequestReason()) && testRequestDTO.getRequestReason().length() > 500){
            invalidRequest("labmanagement.thingnotexceeds", "Request reason", "500");
        }

        if(testRequestDTO.getReferredIn() == null){
            testRequestDTO.setReferredIn(false);
        }

        boolean isSubmittingTestRequests = (testRequestDTO.getTests() != null && !testRequestDTO.getTests().isEmpty()) || testRequestDTO.getReferredIn().equals(false);
        boolean isSubmittingReferralSamples = (testRequestDTO.getSamples() != null && !testRequestDTO.getSamples().isEmpty()) || testRequestDTO.getReferredIn();

        if(!isSubmittingTestRequests && !isSubmittingReferralSamples){
            invalidRequest("labmanagement.fieldrequired", "Tests or samples");
        }

        if(isSubmittingReferralSamples && isSubmittingTestRequests){
            invalidRequest("labmanagement.eithertestsorsamplesnotboth" );
        }

        String referralFacilityName = null;
        ReferralLocation referralFacility = null;

        if(isSubmittingTestRequests) {
            if (StringUtils.isBlank(testRequestDTO.getPatientUuid())) {
                invalidRequest("labmanagement.fieldrequired", "Patient");
            }
            if(testRequestDTO.getReferredIn()){
                invalidRequest("labmanagement.testsnotforreferredin");
            }

            if(testRequestDTO.getTests() == null || testRequestDTO.getTests().isEmpty()){
                invalidRequest("labmanagement.fieldrequired", "Tests");
            }

            if(StringUtils.isBlank(testRequestDTO.getCareSettingUuid())){
                invalidRequest("labmanagement.fieldrequired", "Care setting");
            }

            if(StringUtils.isBlank(testRequestDTO.getProviderUuid())){
                invalidRequest("labmanagement.fieldrequired", "Provider");
            }
        }else{
            if(!testRequestDTO.getReferredIn()){
                invalidRequest("labmanagement.samplesnotfornonreferral");
            }

            if(StringUtils.isBlank(testRequestDTO.getReferralFromFacilityUuid())){
                invalidRequest("labmanagement.fieldrequired", "Referral location");
            }

            if(testRequestDTO.getSamples() == null || testRequestDTO.getSamples().isEmpty()){
                invalidRequest("labmanagement.fieldrequired", "Samples");
            }

            if(StringUtils.isBlank(testRequestDTO.getCareSettingUuid())){
                testRequestDTO.setCareSettingUuid(GlobalProperties.getCareSettingForReferrals());
                if(StringUtils.isBlank(testRequestDTO.getCareSettingUuid())){
                    Optional<CareSetting> outPatientCareSetting = Context.getOrderService().getCareSettings(false).stream()
                            .filter(p-> CareSetting.CareSettingType.OUTPATIENT.equals(p.getCareSettingType()))
                            .findFirst();
                    if(outPatientCareSetting.isPresent()){
                        testRequestDTO.setCareSettingUuid(outPatientCareSetting.get().getUuid());
                    }else {
                        invalidRequest("labmanagement.fieldrequired", "Care setting");
                    }
                }
            }

            if(StringUtils.isBlank(testRequestDTO.getProviderUuid())){
                testRequestDTO.setProviderUuid(GlobalProperties.getUnknownProviderUuid());
                if(StringUtils.isBlank(testRequestDTO.getProviderUuid())) {
                    Provider unknownProvider = Context.getProviderService().getUnknownProvider();
                    if(unknownProvider != null){
                        testRequestDTO.setProviderUuid(unknownProvider.getUuid());
                    }else {
                        invalidRequest("labmanagement.fieldrequired", "Provider");
                    }
                }
            }

            referralFacility = getReferralLocationByUuid(testRequestDTO.getReferralFromFacilityUuid());
            if(referralFacility == null){
                invalidRequest("labmanagement.thingnotexists",  "Referral location");
            }

            if(referralFacility.getPatient() == null){
                invalidRequest("labmanagement.referralLocationNoPatient");
            }
            testRequestDTO.setPatientUuid(referralFacility.getPatient().getUuid());

            String otherReferenceLabConcept = GlobalProperties.getOtherReferenceLabConcept();
            if(referralFacility.getConcept() != null && referralFacility.getConcept().getUuid().equalsIgnoreCase(otherReferenceLabConcept)){
                if(StringUtils.isBlank(testRequestDTO.getReferralFromFacilityName())){
                    invalidRequest("labmanagement.fieldrequired", "Referral location Name");
                }else if(testRequestDTO.getReferralFromFacilityName().length() > 500){
                    invalidRequest("labmanagement.thingnotexceeds", "Referral location Name", "255");
                }
                referralFacilityName = testRequestDTO.getReferralFromFacilityName();
            }

        }

        ConceptService conceptService = Context.getConceptService();
        LocationService locationService = Context.getLocationService();
        Map<String, Location> locations=new HashMap<>();
        Map<String, Concept> sampleTypes=new HashMap<>();
        Map<String, StorageUnit> storageUnits = new HashMap<>();
        Map<String, String> archiveSampleList = new HashMap<>();
        TestConfigSearchFilter testConfigSearchFilter = new TestConfigSearchFilter();
        testConfigSearchFilter.setTestUuids(new ArrayList<>());
        List<TestRequestItemDTO> testRequests = testRequestDTO.getTests();
        if(testRequests == null){
            testRequests = new ArrayList<>();
        }

        Boolean hasRepositoryMutate = null;
        boolean archive = false;
        if(testRequestDTO.getSamples() != null && !testRequestDTO.getSamples().isEmpty()){
            int sampleIndex = 0;
            for(TestRequestSampleDTO sample : testRequestDTO.getSamples()){
                sampleIndex = sampleIndex + 1;
                if(StringUtils.isBlank(sample.getSampleTypeUuid())){
                    invalidRequest("labmanagement.fieldrequired", "Sample type " + sampleIndex);
                }

                if(StringUtils.isBlank(sample.getAccessionNumber())){
                    invalidRequest("labmanagement.fieldrequired", "Sample internal reference/accession number " + sampleIndex);
                }

                if(StringUtils.isBlank(sample.getExternalRef())){
                    invalidRequest("labmanagement.fieldrequired", "Sample external reference " + sampleIndex);
                }

                if(sample.getTests() == null || sample.getTests().isEmpty()){
                    invalidRequest("labmanagement.fieldrequired", "Sample tests " + sampleIndex);
                }

                StorageUnit storageUnit = null;
                if(sample.getArchive() != null && sample.getArchive()){
                    if(StringUtils.isBlank(sample.getStorageUnitUuid())){
                        invalidRequest("labmanagement.fieldrequired", "Sample storage unit " + sampleIndex);
                    }
                    if(hasRepositoryMutate == null){
                        hasRepositoryMutate = Context.getAuthenticatedUser().hasPrivilege(Privileges.TASK_LABMANAGEMENT_REPOSITORY_MUTATE);
                    }
                    if(!hasRepositoryMutate){
                        invalidRequest("labmanagement.notauthorisedtoarchive");
                    }
                    if(!storageUnits.containsKey(sample.getStorageUnitUuid())) {
                        storageUnit = getStorageUnitByUuid(sample.getStorageUnitUuid());
                        if (storageUnit == null) {
                            invalidRequest("labmanagement.thingnotexists", "Storage unit " + sampleIndex);
                        }
                        storageUnits.put(sample.getStorageUnitUuid(), storageUnit);
                    }
                    archive = true;
                }

                int testIndex = 0;
                for(TestRequestItemDTO test : sample.getTests()){
                    testIndex = testIndex + 1;
                    if(StringUtils.isBlank(test.getTestUuid())){
                        invalidRequest("labmanagement.fieldrequired", "Sample test " + testIndex);
                    }

                    if(StringUtils.isBlank(test.getLocationUuid())){
                        invalidRequest("labmanagement.fieldrequired", "Sample test location " + testIndex);
                    }
                    testRequests.add(test);
                }
                int sampleTestCount = sample.getTests().size();
                long uniqueSampleTestCount = sample.getTests().stream().map(TestRequestTestDTO::getTestUuid).distinct().count();
                if(uniqueSampleTestCount != sampleTestCount){
                    invalidRequest("labmanagement.fieldrequired", "No duplicate tests for sample " + testIndex);
                }

                if(sampleTypes.containsKey(sample.getSampleTypeUuid()))
                    continue;

                Concept concept = conceptService.getConceptByUuid(sample.getSampleTypeUuid());
                if(concept == null){
                    invalidRequest("labmanagement.thingnotexists",  "Sample type "+ sampleIndex);
                }
                sampleTypes.putIfAbsent(sample.getSampleTypeUuid(), concept);

            }
        }

        boolean allReferredOut = true;
        int testIndex = 0;
        for(TestRequestTestDTO test : testRequests){
            testIndex = testIndex + 1;
            if(StringUtils.isBlank(test.getTestUuid())){
                invalidRequest("labmanagement.fieldrequired", "Test " + testIndex);
            }
            if(StringUtils.isBlank(test.getLocationUuid())){
                invalidRequest("labmanagement.fieldrequired", "Test location " + testIndex);
            }

            if(test.getReferredOut() == null || !test.getReferredOut()){
                allReferredOut = false;
            }
            testConfigSearchFilter.getTestUuids().add(test.getTestUuid());
            if(locations.containsKey(test.getLocationUuid()))
                continue;
            Location location = locationService.getLocationByUuid(test.getLocationUuid());
            if(location == null){
                invalidRequest("labmanagement.thingnotexists",  "Test location "+ testIndex);
            }
            locations.putIfAbsent(test.getLocationUuid(), location);
        }

        int testCount = testConfigSearchFilter.getTestUuids().size();
        testConfigSearchFilter.setTestUuids(testConfigSearchFilter.getTestUuids().stream().distinct().collect(Collectors.toList()));
        if(!testRequestDTO.getReferredIn() && testCount != testConfigSearchFilter.getTestUuids().size()){
            invalidRequest("labmanagement.duplicatetestsnotallowed");
        }

        testConfigSearchFilter.setVoided(false);
        Map<String,TestConfigDTO> testConfigurations = dao.findTestConfigurations(testConfigSearchFilter).getData().stream().collect(Collectors.toMap(TestConfigDTO::getTestUuid, p->p));
        if(testConfigurations.isEmpty()){
            invalidRequest("labmanagement.thingnotexists",  "All tests");
        }
        testIndex = 0;
        for(TestRequestTestDTO test : testRequests){
            testIndex = testIndex + 1;
            if(!testConfigurations.containsKey(test.getTestUuid())){
                invalidRequest("labmanagement.thingnotexists",  "Test "+ testIndex);
            }
            TestConfigDTO testConfig = testConfigurations.get(test.getTestUuid());
            if(!testConfig.getEnabled()){
                invalidRequest("labmanagement.testnotactive",  testConfig.getTestName());
            }
        }

        PatientService patientService = Context.getPatientService();
        Patient patient = patientService.getPatientByUuid(testRequestDTO.getPatientUuid());
        if(patient == null){
            invalidRequest("labmanagement.thingnotexists",  "Patient");
        }

        OrderService orderService = Context.getOrderService();
        CareSetting careSetting = orderService.getCareSettingByUuid(testRequestDTO.getCareSettingUuid());
        if(careSetting == null){
            invalidRequest("labmanagement.thingnotexists",  "Care setting");
        }

        Location atLocation = locationService.getLocationByUuid(testRequestDTO.getAtLocationUuid());
        if(atLocation == null){
            invalidRequest("labmanagement.thingnotexists",  "Current user location");
        }

        ProviderService providerService = Context.getProviderService();
        Provider provider = providerService.getProviderByUuid(testRequestDTO.getProviderUuid());
        if(provider == null){
            invalidRequest("labmanagement.thingnotexists",  "Provider");
        }

        EncounterService encounterService = Context.getEncounterService();
        Visit visit = null;
        Encounter encounter = null;
        EncounterType encounterType = encounterService.getEncounterTypeByUuid(GlobalProperties.getLaboratoryEncounterType());
        if(encounterType == null){
            invalidRequest("labmanagement.labencountertypenotconfigured");
        }
        Date visitDate = testRequestDTO.getRequestDate();
        Date today = new Date();
        if(DateUtils.isSameDay(visitDate, today))
            visitDate = today;

        boolean queuePatient = false;
        VisitService visitService = Context.getVisitService();
        if(!testRequestDTO.getReferredIn() && (patient.getDead() == null || !patient.getDead()) && !allReferredOut){
             List<Visit> activeVisits = visitService.getActiveVisitsByPatient(patient);
             if(activeVisits.isEmpty()){
                 Visit tempVisit = new Visit();
                 tempVisit.setPatient(patient);
                 tempVisit.setStartDatetime(visitDate);
                 tempVisit.setLocation(locations.values().iterator().next());
                 VisitType visitType = visitService.getVisitTypeByUuid(GlobalProperties.getDefaultVisitType());
                 tempVisit.setVisitType(visitType);
                 visit = visitService.saveVisit(tempVisit);
             }else{
                 activeVisits.sort(Comparator.comparing(Visit::getStartDatetime).reversed());
                 visit = activeVisits.get(0);
                 EncounterSearchCriteria encounterSearchCriteria =
                 new EncounterSearchCriteriaBuilder().setPatient(patient)
                         .setEncounterTypes(Collections.singletonList(encounterType))
                         .setVisits(Collections.singletonList(visit))
                         .setIncludeVoided(false)
                         .createEncounterSearchCriteria();
                 List<Encounter> encounters = encounterService.getEncounters(encounterSearchCriteria);
                 if(!encounters.isEmpty()){
                     encounter = encounters.get(0);
                 }
             }
            queuePatient = true;
        }

        if(encounter == null){
            EncounterRole encounterRole = encounterService.getEncounterRoleByUuid(GlobalProperties.getLaboratoryProviderEncounterRole());
            if(encounterRole == null){
                encounterRole = encounterService.getEncounterRoleByUuid(EncounterRole.UNKNOWN_ENCOUNTER_ROLE_UUID);
                if(encounterRole == null){
                    invalidRequest("labmanagement.labencounterrolenotconfigured");
                }
            }
            encounter = new Encounter();
            encounter.setEncounterType(encounterType);
            encounter.setLocation(atLocation /*locations.isEmpty() ? atLocation : locations.values().iterator().next()*/);
            encounter.setVisit(visit);
            encounter.setEncounterDatetime(visitDate);
            encounter.setPatient(patient);
            encounter.setProvider(encounterRole, provider);
            encounter = encounterService.saveEncounter(encounter);
        }
        Set<Order> orders = new HashSet<>();
        List<TestRequestItem> testRequestItems=new ArrayList<>();
        List<Sample> samples = new ArrayList<>();
        List<TestRequestItemSample> testRequestItemSamples=new ArrayList<>();

        boolean testInHouse = false;
        Optional<User> providerUserAccount;
        if(provider.getPerson() != null){
            providerUserAccount= Context.getUserService().getUsersByPerson(
                    provider.getPerson(),false).stream().findAny();
        }else{
            providerUserAccount= Optional.empty();
        }

        if(testRequestDTO.getReferredIn()){
            for(TestRequestSampleDTO sample : testRequestDTO.getSamples()){

                testInHouse = mapToSample(testRequestDTO, sample, conceptService, encounter, provider, patient,
                        careSetting, atLocation, locations, sampleTypes, visitDate, providerUserAccount, testInHouse,
                        orders, testRequestItems, samples, testRequestItemSamples, archiveSampleList);
            }
        }else{
            for(TestRequestTestDTO test : testRequests){
                testInHouse = mapToTestItem(testRequestDTO, test, conceptService, encounter, provider, patient,
                        careSetting, atLocation, locations, visitDate, providerUserAccount, testInHouse, orders,
                        testRequestItems, null, null);
            }
        }

        TestRequest testRequest = new TestRequest();
        testRequest.setCreator(Context.getAuthenticatedUser());
        testRequest.setDateCreated(new Date());
        testRequest.setRequestDate(testRequestDTO.getRequestDate());
        testRequest.setPatient(patient);
        testRequest.setEncounter(encounter);
        testRequest.setAtLocation(atLocation);
        testRequest.setProvider(provider);
        testRequest.setCareSetting(careSetting);
        testRequest.setReferredIn(testRequestDTO.getReferredIn());
        testRequest.setUrgency(testRequestDTO.getUrgency());
        testRequest.setVisit(visit);
        testRequest.setStatus(allReferredOut ? TestRequestStatus.COMPLETED : TestRequestStatus.IN_PROGRESS);
        if(testRequestDTO.getReferredIn()){
            testRequest.setReferralFromFacility(referralFacility);
            testRequest.setReferralFromFacilityName(referralFacilityName);
            testRequest.setReferralInExternalRef(testRequestDTO.getReferralInExternalRef());
        }

        testRequest.setRequestReason(StringUtils.isBlank(testRequestDTO.getRequestReason()) ? null : testRequestDTO.getRequestReason());
        if(StringUtils.isNotBlank(testRequestDTO.getClinicalNote())){
            testRequest.setClinicalNote(testRequestDTO.getClinicalNote());
            Concept clinicalNotesConcept = conceptService.getConceptByUuid(GlobalProperties.getClinicalNotesConceptUuid());
            if(clinicalNotesConcept != null) {
                Obs clinicalNote = new Obs(patient.getPerson(), clinicalNotesConcept, visitDate, atLocation);
                clinicalNote.setValueText(testRequestDTO.getClinicalNote());
                encounter.setObs(new HashSet<Obs>(Arrays.asList(clinicalNote)));
            }
        }
        encounter.setOrders(orders);
        encounter = encounterService.saveEncounter(encounter);

        testRequest = dao.saveTestRequest(testRequest);
        if(StringUtils.isBlank(testRequest.getRequestNo())) {
            String referralLocationPrefix = null;
            if(testRequestDTO.getReferredIn() && testRequest.getReferralFromFacility() != null){
                referralLocationPrefix = testRequest.getReferralFromFacility().getAcronym();
                if((StringUtils.isNotBlank(referralLocationPrefix) && referralLocationPrefix.length() <= 8)){
                    referralLocationPrefix = "-" + referralLocationPrefix;
                }else{
                   referralLocationPrefix = null;
                }
            }
            testRequest.setRequestNo("LRN" +
                    (testRequestDTO.getReferredIn() ? "-R": allReferredOut ? "-O": "") + (referralLocationPrefix == null ? "": referralLocationPrefix) + "-" +
                    StringUtils.leftPad(Integer.toString(testRequest.getId()), 4, '0'));
            dao.saveTestRequest(testRequest);
        }

        for(TestRequestItem testRequestItem : testRequestItems){
            testRequestItem.setTestRequest(testRequest);
            testRequestItem.setOrder(encounter.getOrders().stream()
                    .filter(p->p.getUuid().equals(testRequestItem.getOrder().getUuid()))
                    .findFirst().orElse(null));
            dao.saveTestRequestItem(testRequestItem);
        }

        for(Sample sample : samples){
            sample.setTestRequest(testRequest);
            dao.saveSample(sample);
        }

        for(TestRequestItemSample testRequestItemSample : testRequestItemSamples){
            dao.saveTestRequestItemSample(testRequestItemSample);
        }

        if(testRequestDTO.getReferredIn() || allReferredOut){
            queuePatient = false;
        }

        if(queuePatient && testInHouse && !isRetrospective(visitDate) && !locations.isEmpty()){

            Location locationTo =  locations.values().iterator().next();
            sendPatientToNextLocation(patient, encounter, provider, locationTo, encounter.getLocation(), PatientQueue.Status.PENDING, true);
        }

        if(archive) {
            for (Sample sample : samples) {
                String storageUnitUuid = archiveSampleList.getOrDefault(sample.getUuid(), null);
                if (storageUnitUuid != null) {
                    StorageUnit storageUnit = storageUnits.getOrDefault(storageUnitUuid, null);
                    if (storageUnit != null) {
                        SampleActivity sampleActivity = getSampleActivity(sample, Context.getAuthenticatedUser(), "Archived at registration");
                        archiveSample(sample, new Date(), sampleActivity, storageUnit, Context.getAuthenticatedUser(), null, sample.getVolume(), sample.getVolumeUnit(), null, Context.getAuthenticatedUser());
                    }
                }
            }
        }

        return testRequest;
    }

    private boolean mapToSample(TestRequestDTO testRequestDTO,
                                TestRequestSampleDTO sampleDTO,
                                ConceptService conceptService,
                                Encounter encounter,
                                Provider provider,
                                Patient patient,
                                CareSetting careSetting,
                                Location atLocation,
                                Map<String, Location> locations,
                                Map<String, Concept> concepts,
                                Date visitDate,
                                Optional<User> providerUserAccount,
                                boolean testInHouse,
                                Set<Order> orders,
                                List<TestRequestItem> testRequestItems,
                                List<Sample> samples,
                                List<TestRequestItemSample> testRequestItemSamples,
                                Map<String, String> archiveSampleList){

        Sample sample = new Sample();
        sample.setCreator(Context.getAuthenticatedUser());
        sample.setDateCreated(new Date());
        sample.setAtLocation(atLocation);
        sample.setSampleType(concepts.get(sampleDTO.getSampleTypeUuid()));
        sample.setContainerType(null);
        sample.setCollectedBy(null);
        sample.setCollectionDate(null);
        sample.setContainerCount(null);
        sample.setAccessionNumber(sampleDTO.getAccessionNumber());
        sample.setProvidedRef(null);
        sample.setExternalRef(sampleDTO.getExternalRef());
        sample.setReferredOut(false);
        sample.setCurrentSampleActivity(null);
        boolean requireApproval = testRequestDTO.getReferredIn() ?
                GlobalProperties.getRequireReferralTestRequestApproval() :
                GlobalProperties.getRequireTestRequestApproval();
        if(requireApproval){
            sample.setStatus(SampleStatus.PENDING);
        }else{
            if(testRequestDTO.getReferredIn()){
                sample.setStatus(SampleStatus.TESTING);
            }else{
                sample.setStatus(SampleStatus.COLLECTION);
            }
        }
        sample.setEncounter(encounter);
        samples.add(sample);
        if(sampleDTO.getArchive() != null && sampleDTO.getArchive()) {
            archiveSampleList.put(sample.getUuid(), sampleDTO.getStorageUnitUuid());
        }

        for(TestRequestTestDTO test: sampleDTO.getTests()){
            test.setReferredOut(false);
            TestRequestItemSample testRequestItemSample = new TestRequestItemSample();
            testRequestItemSample.setSample(sample);
            testRequestItemSample.setCreator(Context.getAuthenticatedUser());
            testRequestItemSample.setDateCreated(new Date());
            testInHouse = mapToTestItem(testRequestDTO, test, conceptService,
                    encounter, provider, patient, careSetting, atLocation,
                    locations, visitDate, providerUserAccount, testInHouse,
                    orders, testRequestItems, testRequestItemSample,  sample);
            testRequestItemSamples.add(testRequestItemSample);
        }

        return testInHouse;
    }

    private boolean mapToTestItem(TestRequestDTO testRequestDTO,
                                  TestRequestTestDTO test,
                                  ConceptService conceptService,
                                  Encounter encounter,
                                  Provider provider,
                                  Patient patient,
                                  CareSetting careSetting,
                                  Location atLocation,
                                  Map<String, Location> locations,
                                  Date visitDate, Optional<User> providerUserAccount,
                                  boolean testInHouse, Set<Order> orders,
                                  List<TestRequestItem> testRequestItems,
                                  TestRequestItemSample testRequestItemSample,
                                  Sample sample) {
        TestOrder testOrder = new TestOrder();
        testOrder.setConcept(conceptService.getConceptByUuid(test.getTestUuid()));
        testOrder.setEncounter(encounter);
        testOrder.setOrderer(provider);
        testOrder.setPatient(patient);
        testOrder.setUrgency(testRequestDTO.getUrgency());
        testOrder.setCareSetting(careSetting);
        testOrder.setFulfillerStatus(null);
        if(sample != null){
            testOrder.setAccessionNumber(sample.getAccessionNumber());
        }

        boolean referredOut = !testRequestDTO.getReferredIn() && test.getReferredOut() != null && test.getReferredOut();
        TestRequestItem testRequestItem = new TestRequestItem();
        testRequestItem.setCreator(Context.getAuthenticatedUser());
        testRequestItem.setDateCreated(new Date());
        testRequestItem.setCompleted(false);
        testRequestItem.setAtLocation(atLocation);
        testRequestItem.setToLocation(locations.get(test.getLocationUuid()));
        testRequestItem.setReferredOut(false);
        testRequestItem.setEncounter(encounter);
        testRequestItem.setRequireRequestApproval(testRequestDTO.getReferredIn() ?
                GlobalProperties.getRequireReferralTestRequestApproval() :
                (!referredOut && GlobalProperties.getRequireTestRequestApproval()));

        if(testRequestItem.getRequireRequestApproval()){
            testRequestItem.setStatus(TestRequestItemStatus.REQUEST_APPROVAL);
        }else{
            if(testRequestDTO.getReferredIn()){
                UpdateTestRequestItemInProgress(testRequestItem, testOrder);
            }else if(!referredOut){
                testRequestItem.setStatus(TestRequestItemStatus.SAMPLE_COLLECTION);
            }
        }

        if(referredOut){
            //testInHouse=false; Do not be tempted to set to false as this variable is tracked across multiple calls
            testRequestItem.setReferredOut(true);
            testRequestItem.setReferralOutOrigin(ReferralOutOrigin.Provider);
            testRequestItem.setReferralOutDate(visitDate);
            testRequestItem.setReferralOutBy(providerUserAccount.orElse(null));
            testOrder.setInstructions("Provider referred request out");
            testOrder.setFulfillerStatus(Order.FulfillerStatus.COMPLETED);
            testRequestItem.setStatus(TestRequestItemStatus.REFERRED_OUT_PROVIDER);
            testRequestItem.setCompleted(true);
            testRequestItem.setCompletedDate(new Date());

        }else{
            testInHouse = true;
        }

        if(sample != null){
            testRequestItem.setInitialSample(sample);
        }

        testRequestItem.setOrder(testOrder);
        orders.add(testOrder);
        testRequestItems.add(testRequestItem);
        if(testRequestItemSample != null){
            testRequestItemSample.setTestRequestItem(testRequestItem);
        }
        return testInHouse;
    }

    public void sendPatientToNextLocation(Patient patient, Encounter encounter, Provider provider, Location locationTo,
                                          Location locationFrom, PatientQueue.Status nextQueueStatus,
                                          boolean completePreviousQueue) {
        PatientQueue patientQueue = new PatientQueue();
        PatientQueueingService patientQueueingService = Context.getService(PatientQueueingService.class);

        if (!patientQueueExists(encounter, locationTo, locationFrom, nextQueueStatus)) {
            PatientQueue previousQueue = null;
            if (completePreviousQueue) {
                previousQueue = completePreviousQueue(patient, encounter.getLocation(), PatientQueue.Status.PENDING);
            }
            patientQueue.setLocationFrom(encounter.getLocation());
            patientQueue.setPatient(encounter.getPatient());
            patientQueue.setLocationTo(locationTo);
            patientQueue.setQueueRoom(locationTo);
            patientQueue.setProvider(provider);
            patientQueue.setEncounter(encounter);
            patientQueue.setStatus(nextQueueStatus);
            patientQueue.setCreator(Context.getAuthenticatedUser());
            patientQueue.setDateCreated(new Date());
            patientQueueingService.assignVisitNumberForToday(patientQueue);
            patientQueueingService.savePatientQue(patientQueue);
        }
    }

    public boolean patientQueueExists(Encounter encounter, Location locationTo, Location locationFrom, PatientQueue.Status status) {
        return  dao.patientQueueExists(encounter, locationTo, locationFrom, status);
    }

    public PatientQueue completePreviousQueue(Patient patient, Location location, PatientQueue.Status searchStatus) {
        PatientQueueingService patientQueueingService = Context.getService(PatientQueueingService.class);
        PatientQueue patientQueue = getPreviousQueue(patient, location, searchStatus);
        if (patientQueue != null) {
            patientQueueingService.completePatientQueue(patientQueue);
        }
        return patientQueue;
    }

    public PatientQueue getPreviousQueue(Patient patient, Location location, PatientQueue.Status status) {
        PatientQueueingService patientQueueingService = Context.getService(PatientQueueingService.class);
        PatientQueue previousQueue = null;

        List<PatientQueue> patientQueueList = patientQueueingService.getPatientQueueList(null, OpenmrsUtil.firstSecondOfDay(new Date()), OpenmrsUtil.getLastMomentOfDay(new Date()), location, null, patient, null);

        if (!patientQueueList.isEmpty()) {
            previousQueue = patientQueueList.get(0);
        }
        return previousQueue;
    }



    private boolean isRetrospective(Date encounterDate) {
        return encounterDate.before(OpenmrsUtil.firstSecondOfDay(new Date()));
    }

    private boolean isRetrospective(Encounter encounter) {
        return encounter.getEncounterDatetime().before(OpenmrsUtil.firstSecondOfDay(new Date()));
    }

    public ReferralLocation getReferralLocationById(Integer id) {
        return dao.getReferralLocationById(id);
    }

    public ReferralLocation getReferralLocationByUuid(String uuid) {
        return dao.getReferralLocationByUuid(uuid);
    }

    public ReferralLocation saveReferralLocation(ReferralLocationDTO referralLocationDTO) {

        ReferralLocation referralLocation;
        boolean isNew;
        if (referralLocationDTO.getUuid() == null) {
            referralLocation = new ReferralLocation();
            referralLocation.setCreator(Context.getAuthenticatedUser());
            referralLocation.setDateCreated(new Date());
            referralLocation.setSystem(false);
            isNew = true;
        } else {
            isNew = false;
            referralLocation = getReferralLocationByUuid(referralLocationDTO.getUuid());
            if (referralLocation == null) {
                invalidRequest("labmanagement.thingnotexists", "Referral Location");
            }

            if(referralLocation.getSystem() != null && referralLocation.getSystem()) {
                boolean newEnabled = referralLocationDTO.getEnabled() == null || referralLocationDTO.getEnabled();
                if(newEnabled == referralLocation.getEnabled()){
                    return referralLocation;
                }
                referralLocation.setEnabled(newEnabled);
                referralLocation.setChangedBy(Context.getAuthenticatedUser());
                referralLocation.setDateChanged(new Date());
                return dao.saveReferralLocation(referralLocation);
            }
            else{
                if(referralLocation.getSystem() == null){
                    referralLocation.setSystem(false);
                }
                referralLocation.setChangedBy(Context.getAuthenticatedUser());
                referralLocation.setDateChanged(new Date());
            }
        }

        if(referralLocationDTO.getReferrerIn() == null){
            referralLocationDTO.setReferrerIn(true);
        }

        if(referralLocationDTO.getReferrerOut() == null){
            referralLocationDTO.setReferrerOut(true);
        }

        if(referralLocationDTO.getEnabled() == null){
            referralLocationDTO.setEnabled(true);
        }

        referralLocation.setReferrerIn(referralLocationDTO.getReferrerIn());
        referralLocation.setReferrerOut(referralLocationDTO.getReferrerOut());
        referralLocation.setEnabled(referralLocationDTO.getEnabled());

        if(referralLocationDTO.getReferrerIn()){
            if(StringUtils.isBlank(referralLocationDTO.getPatientUuid())){
                invalidRequest("labmanagement.fieldrequired", "patientUuid");
            }
            Patient patient = Context.getPatientService().getPatientByUuid(referralLocationDTO.getPatientUuid());
            if (patient != null) {
                referralLocation.setPatient(patient);
            } else {
                invalidRequest("labmanagement.notexists", "patientUuid");
            }
        }else{
            referralLocation.setPatient(null);
        }

        if(!StringUtils.isBlank(referralLocationDTO.getConceptUuid())){
            referralLocation.setName(null);
            if(StringUtils.isBlank(referralLocationDTO.getConceptUuid())){
                invalidRequest("labmanagement.fieldrequired", "Concept");
            }

            Concept concept = Context.getConceptService().getConceptByUuid(referralLocationDTO.getConceptUuid());
            if(concept != null){
                ReferralLocationSearchFilter filter = new ReferralLocationSearchFilter();
                filter.setConceptUuid(referralLocationDTO.getConceptUuid());
                filter.setLimit(2);
                filter.setVoided(false);
                Result<ReferralLocationDTO> referralLocationResult = findReferralLocations(filter);
                if (referralLocationResult.getData().stream().anyMatch(p-> isNew || !p.getUuid().equals(referralLocation.getUuid()))) {
                    invalidRequest("labmanagement.referrallocation.conceptexists");
                }
                referralLocation.setConcept(concept);
            }else{
                invalidRequest("labmanagement.notexists", "conceptUuid");
            }
        }else{
            referralLocation.setConcept(null);
            if(StringUtils.isBlank(referralLocationDTO.getName())){
                invalidRequest("labmanagement.fieldrequired", "Name");
            }
            if(referralLocationDTO.getName().length() > 250){
                invalidRequest("labmanagement.thingnotexceeds", "Name", "255");
            }
            ReferralLocationSearchFilter filter = new ReferralLocationSearchFilter();
            filter.setName(referralLocationDTO.getName());
            filter.setVoided(false);
            filter.setLimit(2);
            Result<ReferralLocationDTO> referralLocationResult = findReferralLocations(filter);
            if (referralLocationResult.getData().stream().anyMatch(p-> isNew || !p.getUuid().equals(referralLocation.getUuid()))) {
                invalidRequest("labmanagement.referrallocation.nameexists");
            }
            referralLocation.setName(referralLocationDTO.getName());
        }

        if(StringUtils.isBlank(referralLocationDTO.getAcronym())){
            invalidRequest("labmanagement.fieldrequired", "Acronym");
        }
        else{
            if(referralLocationDTO.getAcronym().length() > 250){
                invalidRequest("labmanagement.thingnotexceeds", "Acronym", "255");
            }
            ReferralLocationSearchFilter filter = new ReferralLocationSearchFilter();
            filter.setAcronym(referralLocationDTO.getAcronym());
            filter.setVoided(false);
            filter.setLimit(2);
            Result<ReferralLocationDTO> referralLocationResult = findReferralLocations(filter);
            if (referralLocationResult.getData().stream().anyMatch(p-> isNew || !p.getUuid().equals(referralLocation.getUuid()))) {
                invalidRequest("labmanagement.referrallocation.acronymexists");
            }
            referralLocation.setAcronym(referralLocationDTO.getAcronym());
        }


        return dao.saveReferralLocation(referralLocation);
    }

    public Result<ReferralLocationDTO> findReferralLocations(ReferralLocationSearchFilter filter){
        return dao.findReferralLocations(filter);
    }

    public Result<TestRequestDTO> findTestRequests(TestRequestSearchFilter filter){
        Result<TestRequestDTO> result = dao.findTestRequests(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        Map<String, Object> requestContextItems = new HashMap<>();
        for (TestRequestDTO testRequestDTO : result.getData()) {
            testRequestDTO.setRequestContextItems(requestContextItems);
            if (testRequestDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }

        if(!result.getData().isEmpty() && filter.getIncludeTestItems()){
            TestRequestItemSearchFilter testRequestItemSearchFilter=new TestRequestItemSearchFilter();
            testRequestItemSearchFilter.setVoided(false);
            testRequestItemSearchFilter.setTestRequestIds(result.getData().stream().map(TestRequestDTO::getId).collect(Collectors.toList()));
            if(!filter.getIncludeAllTests()){
                testRequestItemSearchFilter.setItemStatuses(filter.getItemStatuses());
                if(!filter.isTestConceptForRequestOnly()) {
                    testRequestItemSearchFilter.setTestConceptIds(filter.getTestConceptIds());
                }
                testRequestItemSearchFilter.setReferredOut(filter.getReferredOut());
                testRequestItemSearchFilter.setItemLocationId(filter.getItemLocationId());
                testRequestItemSearchFilter.setOnlyPendingResultApproval(filter.getOnlyPendingResultApproval());
                testRequestItemSearchFilter.setPendingResultApproval(filter.getPendingResultApproval());
                testRequestItemSearchFilter.setItemMatch(filter.getRequestItemMatch());
            }
            testRequestItemSearchFilter.setIncludeTestSamples(filter.getIncludeTestRequestItemSamples());
            testRequestItemSearchFilter.setIncludeTestResult(filter.getIncludeTestItemTestResult());
            testRequestItemSearchFilter.setIncludeTestResultApprovals(filter.getIncludeTestItemTestResultApprovals());
            testRequestItemSearchFilter.setIncludeTestConcept(filter.getIncludeTestItemConcept());
            testRequestItemSearchFilter.setIncludeTestWorksheetInfo(filter.getIncludeTestItemWorksheetInfo());
            testRequestItemSearchFilter.setPermApproval(filter.getPermApproval());
            if(filter.getPendingResultApproval() != null && filter.getPendingResultApproval()){
                testRequestItemSearchFilter.setIncludeTestSamples(true);
            }
            Result<TestRequestItemDTO> testItems = findTestRequestItems(testRequestItemSearchFilter);
            if(!testItems.getData().isEmpty()){
                Map<String, List<TestRequestItemDTO>> testGroups = testItems.getData().stream().collect(Collectors.groupingBy(TestRequestItemDTO::getTestRequestUuid));
                for(TestRequestDTO testRequest : result.getData()){
                    List<TestRequestItemDTO> tests = testGroups.getOrDefault(testRequest.getUuid(),null);
                    testRequest.setTests(tests == null ? new ArrayList<>() : tests);
                }
            }

        }

        if(!result.getData().isEmpty() && filter.getIncludeTestRequestSamples()){
            SampleSearchFilter sampleSearchFilter=new SampleSearchFilter();
            sampleSearchFilter.setVoided(false);
            sampleSearchFilter.setIncludeTests(true);
            sampleSearchFilter.setTestRequestIds(result.getData().stream().map(TestRequestDTO::getId).collect(Collectors.toList()));
            Result<SampleDTO> samples = findSamples(sampleSearchFilter);
            if(!samples.getData().isEmpty()){
                Map<String, List<SampleDTO>> sampleGroups = samples.getData().stream().collect(Collectors.groupingBy(SampleDTO::getTestRequestUuid));
                for(TestRequestDTO testRequest : result.getData()){
                    List<SampleDTO> sampleDTOS = sampleGroups.getOrDefault(testRequest.getUuid(),null);
                    testRequest.setSamples(new ArrayList<>());
                    if(sampleDTOS != null) {
                        testRequest.getSamples().addAll(sampleDTOS);
                    }
                }
            }

        }
        return result;
    }

    public List<TestRequestItem> getTestRequestItemByOrder(Order  order) {
       return dao.getTestRequestItemByOrder(order);
    }


    public Result<TestRequestItemDTO> findTestRequestItems(TestRequestItemSearchFilter filter){
        Result<TestRequestItemDTO> result = dao.findTestRequestItems(filter);
        boolean setTestResults = filter.getIncludeTestResult();
        Map<Integer, List<TestResultDTO>> testResults = null;
        if(!result.getData().isEmpty() && setTestResults){
            setTestResults = Context.getAuthenticatedUser().hasPrivilege(Privileges.APP_LABMANAGEMENT_TESTRESULTS);
            if(setTestResults) {
                TestResultSearchFilter testResultSearchFilter = new TestResultSearchFilter();
                testResultSearchFilter.setIncludeApprovals(filter.getIncludeTestResultApprovals());
                testResultSearchFilter.setPermApproval(filter.getPermApproval());
                testResultSearchFilter.setTestRequestItemIds(result.getData().stream().map(p -> p.getId()).collect(Collectors.toList()));
                testResultSearchFilter.setVoided(false);
                testResults = findTestResults(testResultSearchFilter).getData().stream().collect(Collectors.groupingBy(TestResultDTO::getTestRequestItemId));
            }
        }

        Map<Integer, List<Concept>> concepts = null;
        if(filter.getIncludeTestConcept()){
            concepts = dao.getConceptsByIds(result.getData().stream().map(TestRequestItemDTO::getOrderConceptId)
                            .distinct().collect(Collectors.toList())).stream()
                    .collect(Collectors.groupingBy(Concept::getConceptId));
        }

        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy(),
                p.getReferralOutBy(),
                p.getRequestApprovalBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        Map<Integer, List<SampleDTO>> sampleRefs = null;
        if(!result.getData().isEmpty() && filter.getIncludeTestSamples()){
            sampleRefs = dao.getTestRequestItemSampleRefs(result.getData().stream().map(p->p.getId()).collect(Collectors.toList()));
        }

        Map<Integer, List<WorksheetItemDTO>> worksheetInfo = null;
        if(!result.getData().isEmpty() && filter.getIncludeTestWorksheetInfo()){
            worksheetInfo = dao.getTestRequestItemWorksheetRefs(result.getData().stream().map(p->p.getId()).collect(Collectors.toList()));
        }


        Map<String, Object> requestContextItems = new HashMap<>();
        for (TestRequestItemDTO testRequestItemDTO : result.getData()) {
            testRequestItemDTO.setRequestContextItems(requestContextItems);
            if(sampleRefs != null && !sampleRefs.isEmpty()){
                testRequestItemDTO.setSamples(sampleRefs.getOrDefault(testRequestItemDTO.getId(), new ArrayList<>()));
            }

            if(worksheetInfo != null && !worksheetInfo.isEmpty()){
                List<WorksheetItemDTO> worksheetItemDTO = worksheetInfo.getOrDefault(testRequestItemDTO.getId(), null);
                if(worksheetItemDTO != null && !worksheetItemDTO.isEmpty()){
                    testRequestItemDTO.setWorksheetNo(worksheetItemDTO.get(0).getWorksheetNo());
                    testRequestItemDTO.setWorksheetUuid(worksheetItemDTO.get(0).getWorksheetUuid());
                }
            }

            if (testRequestItemDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestItemDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestItemDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestItemDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestItemDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestItemDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestItemDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestItemDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestItemDTO.getReferralOutBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestItemDTO.getReferralOutBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestItemDTO.setReferralOutByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestItemDTO.setReferralOutByGivenName(userPersonNameDTO.get().getGivenName());
                    testRequestItemDTO.setReferralOutByMiddleName(userPersonNameDTO.get().getMiddleName());
                }
            }
            if (testRequestItemDTO.getRequestApprovalBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestItemDTO.getRequestApprovalBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestItemDTO.setRequestApprovalFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestItemDTO.setRequestApprovalGivenName(userPersonNameDTO.get().getGivenName());
                    testRequestItemDTO.setRequestApprovalMiddleName(userPersonNameDTO.get().getMiddleName());
                }
            }
            if(setTestResults){
                List<TestResultDTO> testResult = testResults.getOrDefault(testRequestItemDTO.getId(), null);
                if(testResult != null && !testResult.isEmpty()){
                    testRequestItemDTO.setTestResult(testResult.get(0));
                }
            }

            if(filter.getIncludeTestConcept()){
                List<Concept> concept = concepts.getOrDefault(testRequestItemDTO.getOrderConceptId(), null);
                if(concept != null && !concept.isEmpty()){
                    testRequestItemDTO.setTestConcept(concept.get(0));
                }
            }
        }



        return result;
    }

    public TestRequest getTestRequestById(Integer id){
        return dao.getTestRequestById(id);
    }

    public TestRequest getTestRequestByUuid(String uuid){
        return dao.getTestRequestByUuid(uuid);
    }

    public TestRequest saveTestRequest(TestRequest testRequest){
        return dao.saveTestRequest(testRequest);
    }

    public TestRequestItem getTestRequestItemById(Integer id){
        return dao.getTestRequestItemById(id);
    }

    public TestRequestItem getTestRequestItemByUuid(String uuid){
        return dao.getTestRequestItemByUuid(uuid);
    }

    public TestRequestItem saveTestRequestItem(TestRequestItem testRequestItem){
        return dao.saveTestRequestItem(testRequestItem);
    }

    private void UpdateTestRequestReferredOut(TestRequestItem testRequestItem, Order order,  String accessionNumber){
        UpdateTestRequestItemInProgress(testRequestItem, order, accessionNumber);
        testRequestItem.setStatus(TestRequestItemStatus.REFERRED_OUT_LAB);
    }
    private void UpdateTestRequestItemInProgress(TestRequestItem testRequestItem, Order order,  String accessionNumber){
        testRequestItem.setStatus(TestRequestItemStatus.IN_PROGRESS);
        order.setFulfillerStatus(Order.FulfillerStatus.IN_PROGRESS);
        order.setFulfillerComment("To be processed");
        if(StringUtils.isNotBlank(accessionNumber)){
            order.setAccessionNumber(accessionNumber);
        }
    }
    private void UpdateTestRequestItemInProgress(TestRequestItem testRequestItem, Order order){
        UpdateTestRequestItemInProgress(testRequestItem, order, null);
    }

    private void UpdateTestRequestItemRejected(TestRequestItem testRequestItem, Order order, String reason){
        testRequestItem.setStatus(TestRequestItemStatus.CANCELLED);
        order.setFulfillerStatus(Order.FulfillerStatus.DECLINED);
        order.setFulfillerComment(reason);
    }

    public ApprovalDTO approveTestRequestItem(TestRequestAction testRequestAction){
        if(testRequestAction == null){
            invalidRequest("labmanagement.fieldrequired", "Approval information");
        }

        if(testRequestAction.getRecords() == null || testRequestAction.getRecords().isEmpty()){
            invalidRequest("labmanagement.fieldrequired", "Records");
        }

        if(testRequestAction.getRecords().size() > 100){
            invalidRequest("labmanagement.fieldrequired", "Records not exceeding 100");
        }

        if(testRequestAction.getAction() == null){
            invalidRequest("labmanagement.fieldrequired", "Action");
        }

        if(testRequestAction.getAction().equals(ApprovalResult.RETURNED)){
            invalidRequest("labmanagement.testrequestreturnnotsupported");
        }

        if(testRequestAction.getAction().equals(ApprovalResult.REJECTED) && StringUtils.isBlank(testRequestAction.getRemarks())){
            invalidRequest("labmanagement.fieldrequired", "Rejection remarks");
        }

        if(!StringUtils.isBlank(testRequestAction.getRemarks()) && testRequestAction.getRemarks().length() > 500){
            invalidRequest("labmanagement.thingnotexceeds", "Name", "500");
        }

        List<String> records = testRequestAction.getRecords().stream().distinct().collect(Collectors.toList());
        List<TestRequestItem> testRequestItems = dao.getTestRequestItemsByUuid(testRequestAction.getRecords(), false);
        if(testRequestItems.size() != records.size()){
            invalidRequest("labmanagement.notexists",
                    String.format("%1s Record(s)", records.size() - testRequestItems.size()));
        }

        for(TestRequestItem testRequestItem : testRequestItems){
            if(testRequestItem.getStatus() == null ||
                    !TestRequestItemStatus.isRequestApproveable(testRequestItem.getStatus(), testRequestAction.getAction())){
                invalidRequest("labmanagement.approvalnotallowed", testRequestItem.getOrder().getOrderNumber());
            }


            //if(testRequestItem.getRequireRequestApproval() == null || !testRequestItem.getRequireRequestApproval()){
            //    invalidRequest("labmanagement.approvalnotrequired", testRequestItem.getOrder().getOrderNumber());
            //}

            Pair<Boolean, String> canApprove = ApprovalUtils.canApproveRequest(testRequestItem, testRequestAction.getAction());
            if(!canApprove.getValue1()){
                if(!StringUtils.isBlank(canApprove.getValue2())){
                    invalidRequest(canApprove.getValue2());
                }else {
                    invalidRequest("labmanagement.fieldrequired",
                            String.format("Permissions to approve %1s", testRequestItem.getOrder().getOrderNumber()));
                }
            }
        }

        String remarks = StringUtils.isBlank(testRequestAction.getRemarks()) ? null : testRequestAction.getRemarks().trim();
        User currentUser = Context.getAuthenticatedUser();
        for(TestRequestItem testRequestItem : testRequestItems){
            testRequestItem.setRequestApprovalResult(testRequestAction.getAction());
            testRequestItem.setRequestApprovalRemarks(remarks);
            testRequestItem.setRequestApprovalDate(new Date());
            testRequestItem.setRequestApprovalBy(currentUser);
            if(testRequestAction.getAction().equals(ApprovalResult.APPROVED)) {
                if (testRequestItem.getTestRequest().getReferredIn() != null &&
                        testRequestItem.getTestRequest().getReferredIn().equals(Boolean.TRUE)) {
                    UpdateTestRequestItemInProgress(testRequestItem, testRequestItem.getOrder());
                    Context.getOrderService().updateOrderFulfillerStatus(testRequestItem.getOrder(), testRequestItem.getOrder().getFulfillerStatus(), testRequestItem.getOrder().getFulfillerComment());
                    List<Sample>  samples = dao.getSamplesByTestRequestItem(testRequestItem);
                    for(Sample sample : samples){
                        updateSampleForTesting(sample);
                    }

                } else {
                    testRequestItem.setStatus(TestRequestItemStatus.SAMPLE_COLLECTION);
                }
            }else{
                UpdateTestRequestItemRejected(testRequestItem, testRequestItem.getOrder(), remarks);
            }
            dao.saveTestRequestItem(testRequestItem);
        }

        for(Map.Entry<String, List<TestRequestItem>> testRequestGroup :
                testRequestItems.stream().collect(Collectors.groupingBy(x->x.getTestRequest().getUuid())).entrySet()){
            checkCompletion(testRequestGroup.getValue().get(0).getTestRequest());
        }

        ApprovalDTO approvalDTO=new ApprovalDTO();
        approvalDTO.setRemarks(remarks);
        approvalDTO.setResult(testRequestAction.getAction());
        return approvalDTO;
    }

    private void checkCompletion(TestRequest testRequest){
        int pending = 0;
        int completed = 0;
        int cancelled = 0;
        int total = 0;
        for(TestRequestItem testRequestItem : dao.getTestRequestItemsByTestRequestId(
                Collections.singletonList(testRequest.getId()), false)){
            total++;
            if(TestRequestItemStatus.isPending(testRequestItem.getStatus())){
                pending++;
            }
            if(TestRequestItemStatus.isCompletedProcess(testRequestItem.getStatus())){
                completed++;
            }
            if(TestRequestItemStatus.isCancelled(testRequestItem.getStatus())){
                cancelled++;
            }
        }
        if(pending > 0) {
            // All pending a referred-out, send patient back to clinician
            return;
        }
        if(cancelled == total && TestRequestStatus.isNotCompleted(testRequest.getStatus())){
            testRequest.setStatus(TestRequestStatus.CANCELLED);
            testRequest.setDateStopped(new Date());
            testRequest.setChangedBy(Context.getAuthenticatedUser());
            testRequest.setDateChanged(new Date());
            dao.saveTestRequest(testRequest);
        }
        else if(completed > 0){
            testRequest.setStatus(TestRequestStatus.COMPLETED);
            testRequest.setDateStopped(new Date());
            testRequest.setChangedBy(Context.getAuthenticatedUser());
            testRequest.setDateChanged(new Date());
            dao.saveTestRequest(testRequest);
        }
    }

    public List<Integer> findSamples(String text, boolean includeAll, int maxItems){
        return dao.searchSampleReferenceNumbers(text,includeAll,maxItems);
    }

    public Result<SampleDTO> findSamples(SampleSearchFilter filter){
        if(StringUtils.isNotBlank(filter.getSearchText())){
            List<Integer> sampleIds =  findSamples(filter.getSearchText(), false,100);
            if(sampleIds.isEmpty()) return new Result<>(new ArrayList<>(), 0);
            List<Integer> searchSampleIds = new ArrayList<>();
            if(filter.getSampleId() != null){
                searchSampleIds.add(filter.getSampleId());
            }
            else if(filter.getSampleIds() != null){
                searchSampleIds.addAll(filter.getSampleIds());
            }
            if(!searchSampleIds.isEmpty()){
                searchSampleIds.removeIf(p-> !sampleIds.contains(p));
                if(searchSampleIds.isEmpty()) return new Result<>(new ArrayList<>(), 0);
            }else{
                searchSampleIds.addAll(sampleIds);
            }
            if(searchSampleIds.size() == 1){
                filter.setSampleId(searchSampleIds.get(0));
                filter.setSampleIds(null);
            }else{
                filter.setSampleIds(searchSampleIds);
                filter.setSampleId(null);
            }
        }
        Result<SampleDTO> result = dao.findSamples(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy(),
                p.getReferralOutBy(),
                p.getCollectedBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        Map<String, Object> requestContextItems = new HashMap<>();
        for (SampleDTO sampleDTO : result.getData()) {
            sampleDTO.setRequestContextItems(requestContextItems);
            if (sampleDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(sampleDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    sampleDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    sampleDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (sampleDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(sampleDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    sampleDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    sampleDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (sampleDTO.getReferralOutBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(sampleDTO.getReferralOutBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    sampleDTO.setReferralOutByFamilyName(userPersonNameDTO.get().getFamilyName());
                    sampleDTO.setReferralOutByGivenName(userPersonNameDTO.get().getGivenName());
                    sampleDTO.setReferralOutByMiddleName(userPersonNameDTO.get().getMiddleName());
                }
            }

            if (sampleDTO.getCollectedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(sampleDTO.getCollectedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    sampleDTO.setCollectedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    sampleDTO.setCollectedByMiddleName(userPersonNameDTO.get().getMiddleName());
                    sampleDTO.setCollectedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }

        if(filter.getIncludeTests()) {
            Map<Integer, List<TestRequestItemDTO>> sampleTests = filter.getForWorksheet() ?
                    dao.getTestRequestItemRefsByTestRequestSampleIds(result.getData().stream().map(SampleDTO::getTestRequestItemSampleId).collect(Collectors.toList())):
                    dao.getTestRequestItemRefsBySampleIds(result.getData().stream().map(SampleDTO::getId).collect(Collectors.toList()));
            for (SampleDTO sampleDTO : result.getData()) {
                if (filter.getAllTests() || ((filter.getTestRequestItemConceptIds() == null
                        || filter.getTestRequestItemConceptIds().isEmpty()) && filter.getTestItemlocationId() == null &&
                        (filter.getTestRequestItemStatuses() == null
                                || filter.getTestRequestItemStatuses().isEmpty()))) {
                    sampleDTO.setTests(sampleTests.getOrDefault( filter.getForWorksheet() ? sampleDTO.getTestRequestItemSampleId() : sampleDTO.getId(), new ArrayList<>()));
                } else {
                    List<TestRequestItemDTO> tests = sampleTests.getOrDefault(filter.getForWorksheet() ? sampleDTO.getTestRequestItemSampleId() :sampleDTO.getId(), new ArrayList<>());
                    sampleDTO.setTests(tests.isEmpty() ? tests : tests.stream().filter(p ->

                                    (filter.getTestRequestItemConceptIds() == null
                                            || filter.getTestRequestItemConceptIds().isEmpty() ||
                            filter.getTestRequestItemConceptIds().contains(p.getOrderConceptId())) &&
                                            (filter.getTestRequestItemStatuses() == null
                                                    || filter.getTestRequestItemStatuses().isEmpty() ||
                                                    filter.getTestRequestItemStatuses().contains(p.getStatus()))
                                            && (
                                    filter.getTestItemlocationId() == null || filter.getTestItemlocationId().equals(p.getToLocationId())
                                            )
                    ).collect(Collectors.toList()));
                }
            }
        }
        return result;
    }

    public void deleteSampleByUuid(String sampleUuid, String reason){
        if(StringUtils.isBlank(reason)){
            invalidRequest("labmanagement.fieldrequired", "reason");
        }else if(reason.length() > 255){
            invalidRequest("labmanagement.thingnotexceeds", "Reason", "255");
        }

        Sample sample = dao.getSampleByUuid(sampleUuid);
        if(sample == null){
            invalidRequest("labmanagement.notexists", "Sample");
        }

        List<WorksheetItem> worksheetItems = dao.getWorksheetItemsBySampleId(sample.getId(), false);
        if(!worksheetItems.isEmpty()){
            invalidRequest("labmanagement.thinginuse", "Sample", "worksheet item(s)");
        }

        List<TestResult> testResults = dao.getTestResultsBySampleId(sample.getId(), false);
        if(testResults.isEmpty()){
            invalidRequest("labmanagement.thinginuse", "Sample", "test results");
        }

        List<Sample> childSamples = dao.getChildSamples(sample);
        if(!childSamples.isEmpty() && childSamples.stream().anyMatch(p->!p.getVoided())){
            invalidRequest("labmanagement.thinginuse", "Sample", "other samples");
        }

        sample.setVoided(true);
        sample.setVoidedBy(Context.getAuthenticatedUser());
        sample.setDateVoided(new Date());
        sample.setVoidReason(reason);
        dao.saveSample(sample);
    }


    public  Pair<Sample, Map<Integer, String>> saveSample(SampleDTO sampleDTO){
        Sample sample;
        boolean isNew;
        TestRequest testRequest = null;
        if (sampleDTO.getUuid() == null) {
            isNew = true;
            sample = new Sample();
            sample.setCreator(Context.getAuthenticatedUser());
            sample.setDateCreated(new Date());

            testRequest = getTestRequestByUuid(sampleDTO.getTestRequestUuid());
            if(testRequest == null){
                invalidRequest("labmanagement.thingnotexists",  "Test request");
            }
            sample.setTestRequest(testRequest);

            Location atLocation = Context.getLocationService().getLocationByUuid(sampleDTO.getAtLocationUuid());
            if(atLocation == null){
                invalidRequest("labmanagement.thingnotexists",  "Current user location");
            }
            sample.setAtLocation(atLocation);
            sample.setCollectedBy(Context.getAuthenticatedUser());
            sample.setCollectionDate(new Date());
            sample.setStatus(SampleStatus.COLLECTION);
            sample.setEncounter(testRequest.getEncounter());

        } else {
            isNew = false;
            testRequest = getTestRequestByUuid(sampleDTO.getTestRequestUuid());
            if(testRequest == null){
                invalidRequest("labmanagement.thingnotexists",  "Test request");
            }

            sample = dao.getSampleByUuid(sampleDTO.getUuid());
            if (sample == null) {
                invalidRequest("labmanagement.thingnotexists", "Sample");
            }
            sample.setChangedBy(Context.getAuthenticatedUser());
            sample.setDateChanged(new Date());
        }

        if(StringUtils.isBlank(sampleDTO.getSampleTypeUuid())){
            invalidRequest("labmanagement.fieldrequired", "Sample type");
        }

        if(StringUtils.isBlank(sampleDTO.getAccessionNumber())){
            invalidRequest("labmanagement.fieldrequired", "Sample internal reference/accession number");
        }

        if(sample.getTestRequest().getReferredIn()) {
            if (StringUtils.isBlank(sample.getExternalRef())) {
                invalidRequest("labmanagement.fieldrequired", "Sample external reference");
            }
        }

        boolean archive = false;
        StorageUnit storageUnit = null;
        if(sampleDTO.getArchive() != null && sampleDTO.getArchive()){
            if(StringUtils.isBlank(sampleDTO.getStorageUnitUuid())){
                invalidRequest("labmanagement.fieldrequired", "Sample storage unit");
            }
            if(!Context.getAuthenticatedUser().hasPrivilege(Privileges.TASK_LABMANAGEMENT_REPOSITORY_MUTATE)){
                invalidRequest("labmanagement.notauthorisedtoarchive");
            }
            storageUnit = getStorageUnitByUuid(sampleDTO.getStorageUnitUuid());
            if(storageUnit == null){
                invalidRequest("labmanagement.thingnotexists", "Storage unit");
            }

            if(!isNew){
                if(!SampleStatus.canArchive(sample)){
                    invalidRequest("labmanagement.actionnotallowed", "Archive", sample.getAccessionNumber());
                }
            }

            archive = true;
        }

        boolean requireContainerInfo = StringUtils.isNotBlank(sampleDTO.getContainerTypeUuid()) || sampleDTO.getContainerCount() != null;
        if(requireContainerInfo){
            if(StringUtils.isBlank(sampleDTO.getContainerTypeUuid())){
                invalidRequest("labmanagement.fieldrequired", "Container type");
            }
            if(sampleDTO.getContainerCount() == null){
                sampleDTO.setContainerCount(1);
                invalidRequest("labmanagement.fieldrequired", "Container count");
            }
            if(sampleDTO.getContainerCount() < 0){
                invalidRequest("labmanagement.fieldrequired", "Container count greater than zero");
            }
        }

        boolean requireVolumeInfo = StringUtils.isNotBlank(sampleDTO.getVolumeUnitUuid()) || sampleDTO.getVolume() != null;
        if(requireVolumeInfo){
            if(StringUtils.isBlank(sampleDTO.getVolumeUnitUuid())){
                invalidRequest("labmanagement.fieldrequired", "Volume unit");
            }
            if(sampleDTO.getVolume() == null){
                invalidRequest("labmanagement.fieldrequired", "Volume");
            }
            if(sampleDTO.getVolume().compareTo(BigDecimal.ZERO) < 0){
                invalidRequest("labmanagement.fieldrequired", "Volume greater than or equal to zero");
            }
        }

        ConceptService conceptService = Context.getConceptService();
        Concept sampleType = conceptService.getConceptByUuid(sampleDTO.getSampleTypeUuid());
        if(sampleType == null){
            invalidRequest("labmanagement.thingnotexists",  "Sample type");
        }

        Concept containerType = null;
        if(StringUtils.isNotBlank(sampleDTO.getContainerTypeUuid())) {
            containerType = conceptService.getConceptByUuid(sampleDTO.getContainerTypeUuid());
            if (containerType == null) {
                invalidRequest("labmanagement.thingnotexists", "Container type");
            }
        }

        Concept volumeUnit = null;
        if(StringUtils.isNotBlank(sampleDTO.getVolumeUnitUuid())) {
            volumeUnit = conceptService.getConceptByUuid(sampleDTO.getVolumeUnitUuid());
            if (volumeUnit == null) {
                invalidRequest("labmanagement.thingnotexists", "Volume unit");
            }
        }

        if(sampleDTO.getReferredOut() == null){
            sampleDTO.setReferredOut(isNew ? false : (sample.getReferredOut() != null && sample.getReferredOut()));
        }

        ReferralLocation referralFacility = null;
        List<TestRequestItemSample> currentSampleRequestItemSamples;
        if(!isNew){
            currentSampleRequestItemSamples = dao.getTestRequestItemSamples(sample);
        } else {
            currentSampleRequestItemSamples = null;
        }
        if(sampleDTO.getReferredOut()){
            if(StringUtils.isBlank(sampleDTO.getReferralToFacilityUuid())){
                invalidRequest("labmanagement.fieldrequired", "Referral location");
            }
            referralFacility = getReferralLocationByUuid(sampleDTO.getReferralToFacilityUuid());
            if(referralFacility == null){
                invalidRequest("labmanagement.thingnotexists",  "Referral location");
            }

           /* if(!isNew && currentSampleRequestItemSamples != null && currentSampleRequestItemSamples.stream().anyMatch(p-> p.getTestRequestItem() != null &&
                    !p.getTestRequestItem().getVoided() &&
                    !TestRequestItemStatus.canModifyReferralInformation(p.getTestRequestItem().getStatus()))){
                invalidRequest("labmanagement.samplereferralcannotchange");
            }*/
        }

        boolean canModifyTests = true;
        if(!isNew){
            canModifyTests = false;
             if(TestRequestStatus.isNotCompleted(testRequest.getStatus())){
                 if(currentSampleRequestItemSamples != null){
                     if(currentSampleRequestItemSamples.isEmpty()){
                         canModifyTests = true;
                     }else{
                         // Add non modifieable current sample requests in order to allow saving
                         TestRequest finalTestRequest = testRequest;
                         List<TestRequestItemSample> nonModifiableCurrentSampleRequestItemSamples =
                          currentSampleRequestItemSamples.stream().filter(p-> p.getTestRequestItem() != null &&
                                 !p.getTestRequestItem().getVoided() &&
                                 !p.getTestRequestItem().getTestRequest().getId().equals(finalTestRequest.getId()) &&
                                 !TestRequestItemStatus.canModifyTestSamples(p.getTestRequestItem().getStatus())).collect(Collectors.toList());
                         if(nonModifiableCurrentSampleRequestItemSamples.isEmpty()){
                             canModifyTests = true;
                         }else{
                             if(sampleDTO.getSampleTestItemUuids() == null){
                                 sampleDTO.setSampleTestItemUuids(new HashSet<>());
                             }
                             for(TestRequestItemSample nonModifiableCurrentSampleRequestItemSample : nonModifiableCurrentSampleRequestItemSamples ){
                                 sampleDTO.getSampleTestItemUuids().add(nonModifiableCurrentSampleRequestItemSample.getTestRequestItem().getUuid());
                             }
                             canModifyTests = true;
                         }
                     }
                 }
             }
            if(canModifyTests && (sampleDTO.getSampleTestItemUuids() == null || sampleDTO.getSampleTestItemUuids().isEmpty())){
                canModifyTests = false;
            }
        }

        Map<String,TestRequestItem> testRequestItems = new HashMap<>();
        if(canModifyTests) {
            if ((sampleDTO.getSampleTestItemUuids() == null || sampleDTO.getSampleTestItemUuids().isEmpty())) {
                invalidRequest("labmanagement.fieldrequired", "Sample tests");
            }

            if(sampleDTO.getSampleTestItemUuids() != null && !sampleDTO.getSampleTestItemUuids().isEmpty()){
                validateSampleTestItems(testRequest, sample, sampleDTO.getTestRequestUuid(), sampleDTO.getReferredOut(), sampleDTO.getSampleTestItemUuids(), isNew, testRequestItems, true);
            }
        }

        sample.setSampleType(sampleType);
        sample.setContainerType(containerType);
        sample.setContainerCount(sampleDTO.getContainerCount());
        sample.setAccessionNumber(sampleDTO.getAccessionNumber());

        boolean isReferredOut = sampleDTO.getReferredOut() != null && sampleDTO.getReferredOut();
        boolean oldReferredOut = !isNew && sample.getReferredOut();
        sample.setReferredOut(isReferredOut);
        if(!isNew && !isReferredOut && oldReferredOut){
            invalidRequest("labmanagement.samplereferredoutcantchange");
        }

        if(isReferredOut){
            if(!oldReferredOut) {
                sample.setReferralOutBy(Context.getAuthenticatedUser());
                sample.setReferralOutOrigin(ReferralOutOrigin.Laboratory);
                sample.setReferralOutDate(new Date());
            }
            sample.setProvidedRef(sampleDTO.getProvidedRef());
            sample.setReferralToFacility(referralFacility);
            sample.setReferralToFacilityName(null);
        }else{
            sample.setReferralOutBy(null);
            sample.setReferralOutOrigin(null);
            sample.setReferralOutDate(null);
            sample.setReferralToFacility(null);
            sample.setReferralToFacilityName(null);
            sample.setProvidedRef(null);
        }

        sample.setVolume(sampleDTO.getVolume());
        sample.setVolumeUnit(volumeUnit);
        sample = dao.saveSample(sample);

        List<Integer> testRequestItemsToDelete = null;
        List<TestRequestItemSample> samplesAdded = null;
        if(canModifyTests) {
            Set<String> testsToAdd = sampleDTO.getSampleTestItemUuids();
            if(!isNew) {
                TestRequest finalTestRequest1 = testRequest;
                testRequestItemsToDelete =  currentSampleRequestItemSamples.
                        stream().
                        filter(p->!p.getVoided() && p.getTestRequestItem().getTestRequest().getId().equals(finalTestRequest1.getId()) &&
                        sampleDTO.getSampleTestItemUuids().stream().noneMatch(x -> p.getTestRequestItem().getUuid().equalsIgnoreCase(x)))
                        .map(p->p.getId())
                        .collect(Collectors.toList());
                if(!testRequestItemsToDelete.isEmpty()){
                    dao.deleteTestRequestItemSamples(testRequestItemsToDelete);
                }

                testsToAdd = new HashSet<>(sampleDTO.getSampleTestItemUuids().
                        stream().
                        filter(p-> currentSampleRequestItemSamples.stream()
                                .noneMatch(x-> !x.getVoided() &&
                                x.getTestRequestItem().getUuid().equalsIgnoreCase(p)))
                        .collect(Collectors.toList()));
            }

            samplesAdded=new ArrayList<>();
            saveTestRequestItemSamples(testsToAdd, sample, testRequestItems, samplesAdded);
        }

        Map<Integer, String> instructionUpdate = null;
        instructionUpdate = processReleaseItemsForTesting(archive, sample, testRequest, isNew, samplesAdded, oldReferredOut, isReferredOut);

        if(archive && storageUnit != null){
            SampleActivity sampleActivity = getSampleActivity(sample, Context.getAuthenticatedUser(), "Archived at collection");
            archiveSample(sample, new Date(), sampleActivity, storageUnit, Context.getAuthenticatedUser(), null, sample.getVolume(), sample.getVolumeUnit(), null, Context.getAuthenticatedUser());
        }

        return new Pair<>(sample, instructionUpdate);
    }

    private Map<Integer, String> processReleaseItemsForTesting(boolean archive, Sample sample, TestRequest testRequest, boolean isNew, List<TestRequestItemSample> samplesAdded, boolean oldReferredOut, boolean isReferredOut) {
        Map<Integer, String> instructionUpdate = null;
        boolean requireAutoRelease = archive || !sample.getTestRequest().getId().equals(testRequest.getId());
        boolean autoReleaseSamplesForTesting = GlobalProperties.getAutoReleaseSamples() || requireAutoRelease;
        if(isNew || (SampleStatus.canReleaseSamplesForTesting(sample.getStatus()) &&
                TestRequestStatus.canReleaseSamplesForTesting(testRequest.getStatus()))){
            // Auto release for new requests
            if(autoReleaseSamplesForTesting) {
                releaseSamplesForTesting(testRequest.getUuid(), Arrays.asList(sample.getUuid()));
            }
        }else{
            if(samplesAdded != null && !samplesAdded.isEmpty() && TestRequestStatus.canReleaseSamplesForTesting(testRequest.getStatus())) {
                if((SampleStatus.canReleaseAdditionalTestItemsForTesting(sample.getStatus()) || requireAutoRelease)) {
                    Map<String, Pair<TestRequestItem,Map<String,Sample>>> testRequestItemSamplesForRelease = new HashMap<>();
                    for(TestRequestItemSample testRequestItemSample : samplesAdded){
                        if(!testRequestItemSamplesForRelease.containsKey(testRequestItemSample.getTestRequestItem().getUuid())){
                            testRequestItemSamplesForRelease.put(testRequestItemSample.getTestRequestItem().getUuid(), new Pair<>(testRequestItemSample.getTestRequestItem(),new HashMap<>()));
                        }
                        testRequestItemSamplesForRelease.get(testRequestItemSample.getTestRequestItem().getUuid()).getValue2().putIfAbsent(sample.getUuid(), sample);
                    }
                    instructionUpdate = internalReleaseForTesting(testRequestItemSamplesForRelease);
                }
            }

            if((SampleStatus.canReleaseAdditionalTestItemsForTesting(sample.getStatus()) || requireAutoRelease) && !isNew && !oldReferredOut && isReferredOut){
                List<TestRequestItemSample> newCurrentSampleRequestItemSamples = dao.getTestRequestItemSamples(sample);

                List<TestRequestItemSample> finalSamplesAdded = samplesAdded;
                TestRequest finalTestRequest2 = testRequest;
                newCurrentSampleRequestItemSamples = newCurrentSampleRequestItemSamples.stream().filter(p-> p.getTestRequestItem() != null &&
                        !p.getTestRequestItem().getVoided() && p.getTestRequestItem().getTestRequest().getId().equals(finalTestRequest2.getId()) && (finalSamplesAdded == null  || finalSamplesAdded.isEmpty() ||
                        finalSamplesAdded.stream().noneMatch(x->x.getId().equals(p.getId()))
                        ) ).collect(Collectors.toList());

                Map<String, Pair<TestRequestItem,Map<String,Sample>>> testRequestItemSamplesForRelease = new HashMap<>();
                for(TestRequestItemSample testRequestItemSample : newCurrentSampleRequestItemSamples){
                    if(!TestRequestItemStatus.canModifyReferralInformation(testRequestItemSample.getTestRequestItem().getStatus())){
                        invalidRequest("labmanagement.samplereferralcannotchange");
                    }
                    if(!testRequestItemSamplesForRelease.containsKey(testRequestItemSample.getTestRequestItem().getUuid())){
                        testRequestItemSamplesForRelease.put(testRequestItemSample.getTestRequestItem().getUuid(), new Pair<>(testRequestItemSample.getTestRequestItem(),new HashMap<>()));
                    }
                    testRequestItemSamplesForRelease.get(testRequestItemSample.getTestRequestItem().getUuid()).getValue2().putIfAbsent(sample.getUuid(), sample);
                }
                instructionUpdate = internalReleaseForTesting(testRequestItemSamplesForRelease);
            }
        }
        return instructionUpdate;
    }

    protected void saveTestRequestItemSamples(Set<String> testsToAdd, Sample sample, Map<String, TestRequestItem> testRequestItems, List<TestRequestItemSample> samplesAdded) {
        for(String testToAdd : testsToAdd){
            TestRequestItemSample testRequestItemSample = new TestRequestItemSample();
            testRequestItemSample.setSample(sample);
            testRequestItemSample.setTestRequestItem(testRequestItems.get(testToAdd));
            samplesAdded.add(dao.saveTestRequestItemSample(testRequestItemSample));

            if(testRequestItemSample.getTestRequestItem().getInitialSample() == null ||
                    !testRequestItemSample.getTestRequestItem().getInitialSample().getId().equals(sample.getId())) {
                testRequestItemSample.getTestRequestItem().setInitialSample(sample);
                dao.saveTestRequestItem(testRequestItemSample.getTestRequestItem());
            }

        }
    }

    protected void validateSampleTestItems(TestRequest testRequest, Sample sample, String testRequestUuid, boolean referredOut, Set<String> sampleTestItemUuids, boolean isNew, Map<String, TestRequestItem> testRequestItems, boolean checkSameTestRequest) {
        int index = 1;
        for(String sampleTestItemUuid : sampleTestItemUuids){
            TestRequestItem testRequestItem = dao.getTestRequestItemByUuid(sampleTestItemUuid);

            if(testRequestItem == null || testRequestItem.getVoided()){
                invalidRequest("labmanagement.thingnotexists",  "Test " + index);
            }

            if(checkSameTestRequest) {
                // Skip tests not belonging to this test request
                if (!testRequestItem.getTestRequest().getId().equals(testRequest.getId())) {
                    continue;
                }
                if (!testRequestItem.getTestRequest().getUuid().equals(testRequestUuid)) {
                    invalidRequest("labmanagement.thingnotexists", "Request test " + index);
                }
            }

            Sample finalSample = sample;
            List<TestRequestItemSample> associatedSamples = dao.getTestRequestItemSamples(testRequestItem, false)
                    .stream().filter(p-> isNew || !p.getSample().getUuid().equalsIgnoreCase(finalSample.getUuid())).collect(Collectors.toList());
            if(!associatedSamples.isEmpty()){
                    invalidRequest("labmanagement.testalreadyassociatedwithsample",  "Test " + index, associatedSamples.get(0).getSample().getAccessionNumber());
            }

            long testItemsExistingSamplesReferralValue = 0;
            long samplesAssociatedWithTestItem = 0;
            if (testRequestItem.getTestRequestItemSamples() != null) {

                boolean finalIsNew = isNew;
                Sample finalSample1 = sample;
                List<Sample> samplesAssociatedWithTestRequest = testRequestItem.getTestRequestItemSamples().stream()
                        .filter(p -> !p.getVoided() && (finalIsNew ||
                                !finalSample1.getId().equals(p.getSample().getId()))
                        ).map(TestRequestItemSample::getSample).collect(Collectors.toList());
                samplesAssociatedWithTestItem = samplesAssociatedWithTestRequest.size();
                testItemsExistingSamplesReferralValue = samplesAssociatedWithTestRequest.stream()
                        .filter(p -> !p.getVoided() &&
                                p.getReferredOut() != null && p.getReferredOut()
                        ).count();
            }

            if (samplesAssociatedWithTestItem > 0) {
                boolean mixOfReferral = false;
                if (testItemsExistingSamplesReferralValue > 0) {
                    if (!referredOut) {
                        mixOfReferral = true;
                    }
                } else if (referredOut) {
                    mixOfReferral = true;
                }
                if (mixOfReferral) {
                    invalidRequest("labmanagement.mixingreferredoutnotallowed", "" + index);
                }
            }

            index++;
            testRequestItems.putIfAbsent(sampleTestItemUuid, testRequestItem);

        }
    }

    public Pair<Sample, Map<Integer, String>> useExistingSampleForTest(TestRequestAction testRequestAction){
        if(testRequestAction == null){
            invalidRequest("labmanagement.fieldrequired", "Action information");
        }

        if(testRequestAction.getRecords() == null || testRequestAction.getRecords().isEmpty()){
            invalidRequest("labmanagement.fieldrequired", "Records");
        }

        if(StringUtils.isBlank(testRequestAction.getTestRequestUuid())){
            invalidRequest("labmanagement.fieldrequired", "Test request");
        }

        if(StringUtils.isBlank(testRequestAction.getUuid())){
            invalidRequest("labmanagement.fieldrequired", "Sample");
        }

        TestRequest testRequest = getTestRequestByUuid(testRequestAction.getTestRequestUuid());
        if(testRequest == null){
            invalidRequest("labmanagement.thingnotexists",  "Test request");
        }

        Sample sample = getSampleByUuid(testRequestAction.getUuid());
        if(sample == null){
            invalidRequest("labmanagement.thingnotexists",  "Sample");
        }

        Set<String> records = new HashSet<>(testRequestAction.getRecords());
        Map<String, TestRequestItem> testRequestItems = new HashMap<>();
        validateSampleTestItems(testRequest, sample,
                testRequestAction.getTestRequestUuid(),
                sample.getReferredOut() != null && sample.getReferredOut(),records,false,testRequestItems, false);

        if(!TestRequestStatus.isNotCompleted(testRequest.getStatus())){
            invalidRequest("labmanagement.actionnotallowed", "Test request modification ", testRequest.getRequestNo());
        }

        List<TestRequestItemSample> samplesAdded=new ArrayList<>();
        saveTestRequestItemSamples(records, sample, testRequestItems, samplesAdded);

        Map<Integer, String> instructionUpdate = null;
        boolean referredOut = sample.getReferredOut() != null && sample.getReferredOut();
        instructionUpdate = processReleaseItemsForTesting(false, sample, testRequest, false, samplesAdded, referredOut, referredOut);
        return new Pair<>(sample, instructionUpdate);
    }

    public void deleteSampleByUuid(String sampleUuid){
        Sample sample = dao.getSampleByUuid(sampleUuid);
        if(sample == null){
            invalidRequest("labmanagement.thingnotexists",  "Sample");
        }
        if(!SampleStatus.canDeleteSampleWithStatus(sample.getStatus())){
            invalidRequest("labmanagement.sampleinuse");
        }

        List<TestResult> testResults = dao.getSampleTestResults(sample);
        if(testResults != null && !testResults.isEmpty()){
            invalidRequest("labmanagement.sampleinuse");
        }

        List<SampleActivity> sampleActivities = dao.getSampleActivityBySample(sample);
        if(sampleActivities != null && !sampleActivities.isEmpty()){
            invalidRequest("labmanagement.sampleinuse");
        }

        List<TestRequestItemSample> testRequestItemSamples = dao.getTestRequestItemSamples(sample);
        if(testRequestItemSamples != null && !testRequestItemSamples.isEmpty()){
            dao.deleteTestRequestItemSamples(testRequestItemSamples.stream().map(p->p.getId())
                    .collect(Collectors.toList()));
        }
        dao.deleteSampleById(sample.getId());
    }

    public Map<Integer, String> releaseSamplesForTesting(String testRequestUuid, List<String> sampleUuids){
        if(StringUtils.isBlank(testRequestUuid)){
            invalidRequest("labmanagement.fieldrequired", "Test Request");
        }
        if(sampleUuids == null || sampleUuids.isEmpty()) return new HashMap<Integer, String>();
        TestRequest testRequest = dao.getTestRequestByUuid(testRequestUuid);
        if(testRequest == null || testRequest.getVoided()){
            invalidRequest("labmanagement.thingnotexists",  "Test Request");
        }

        if(!TestRequestStatus.canReleaseSamplesForTesting(testRequest.getStatus())){
            invalidRequest("labmanagement.thingdoesnotallowsamplereleasefortesting",  "Test Request");
        }

        List<Sample> samples = dao.getTestRequestSamplesByUuid(sampleUuids);
        if(samples.isEmpty()){
            invalidRequest("labmanagement.thingnotexists",  "Samples");
        }

        if(samples.size() != sampleUuids.size()){
            invalidRequest("labmanagement.thingnotexists",  "One of the Samples");
        }

        Map<String, Pair<TestRequestItem,Map<String,Sample>>> testRequestItemSamples = new HashMap<>();
        for(Sample sample : samples){

            List<TestRequestItemSample> sampleTestRequestSamples = dao.getTestRequestItemSamples(sample)
                    .stream()
                    .filter(p->p.getTestRequestItem().getTestRequest().getId().equals(testRequest.getId()))
                    .collect(Collectors.toList());
            if(sampleTestRequestSamples == null || sampleTestRequestSamples.isEmpty() ||
                    sampleTestRequestSamples.stream().allMatch(BaseOpenmrsData::getVoided)
            ){
                invalidRequest("labmanagement.thingdoesnotallowsamplereleasefortesting",  "Sample "+  sample.getAccessionNumber() + " test items");
            }

            for(TestRequestItemSample testRequestItemSample : sampleTestRequestSamples){
                if(testRequestItemSample.getTestRequestItem().getVoided()
                        || !TestRequestItemStatus.canReleaseSamplesForTesting(testRequestItemSample.getTestRequestItem().getStatus())){
                    invalidRequest("labmanagement.thingdoesnotallowsamplereleasefortesting",  "Sample "+  sample.getAccessionNumber() + " test item " + testRequestItemSample.getTestRequestItem().getId());
                }

                String testRequestItemUuid = testRequestItemSample.getTestRequestItem().getUuid().toLowerCase();
                if(!testRequestItemSamples.containsKey(testRequestItemUuid)){
                    testRequestItemSamples.put(testRequestItemUuid, new Pair<>(testRequestItemSample.getTestRequestItem(),new HashMap<>()));
                }
                testRequestItemSamples.get(testRequestItemUuid).getValue2().putIfAbsent(sample.getUuid(), sample);
            }

        }

        for(Map.Entry<String, Pair<TestRequestItem,Map<String,Sample>>> testRequestSamples : testRequestItemSamples.entrySet()){
            long countOfReferredOut = testRequestSamples.getValue().getValue2().entrySet().stream()
                    .filter(p->p.getValue().getReferredOut())
                    .count();
            if(countOfReferredOut > 0 && countOfReferredOut != testRequestSamples.getValue().getValue2().size()){
                invalidRequest("labmanagement.mixingreferredoutnotallowed", testRequestSamples.getValue()
                        .getValue2().entrySet().stream().findAny().get().getValue().getAccessionNumber());
            }
        }

        Map<Integer, String> instructionUpdate = internalReleaseForTesting(testRequestItemSamples);
        return instructionUpdate;
    }

    private Map<Integer, String> internalReleaseForTesting(Map<String, Pair<TestRequestItem, Map<String, Sample>>> testRequestItemSamples) {
        OrderService orderService = Context.getOrderService();

        Map<Integer, String> instructionUpdate = new HashMap<>();
        for(Map.Entry<String, Pair<TestRequestItem,Map<String,Sample>>> testRequestSamples : testRequestItemSamples.entrySet()){
            TestRequestItem testRequestItem = testRequestSamples.getValue().getValue1();
            Optional<Sample> referredOutSample = testRequestSamples.getValue().getValue2().values().stream()
                    .filter(Sample::getReferredOut)
                    .findFirst();
            if(referredOutSample.isPresent()){
                testRequestItem.setReferredOut(true);
                testRequestItem.setReferralToFacility(referredOutSample.get().getReferralToFacility());
                testRequestItem.setReferralOutDate(referredOutSample.get().getReferralOutDate());
                testRequestItem.setReferralOutBy(referredOutSample.get().getReferralOutBy());
                testRequestItem.setReferralToFacilityName(referredOutSample.get().getReferralToFacilityName());
                testRequestItem.setStatus(TestRequestItemStatus.REFERRED_OUT_LAB);
                testRequestItem.setChangedBy(Context.getAuthenticatedUser());
                testRequestItem.setDateChanged(new Date());
                testRequestItem.setInitialSample(referredOutSample.get());
                testRequestItem.setReferralOutOrigin(ReferralOutOrigin.Laboratory);
                testRequestItem.setReferralOutSample(referredOutSample.get());

                Order order = testRequestItem.getOrder();
                order.getId();
                UpdateTestRequestReferredOut(testRequestItem, order, referredOutSample.get().getAccessionNumber());
                orderService.updateOrderFulfillerStatus(order,
                        order.getFulfillerStatus(), order.getFulfillerComment(), order.getAccessionNumber());
                instructionUpdate.put(order.getId(), "REFER TO "+referredOutSample.get().getReferralToFacility().getAcronym());
            }else{

               Optional<Sample> initialSample = testRequestSamples.getValue().getValue2().values().stream()
                        .findFirst();
                testRequestItem.setReferredOut(false);
                testRequestItem.setReferralToFacility(null);
                testRequestItem.setReferralOutDate(null);
                testRequestItem.setReferralOutBy(null);
                testRequestItem.setReferralToFacilityName(null);
                testRequestItem.setStatus(TestRequestItemStatus.IN_PROGRESS);
                testRequestItem.setChangedBy(Context.getAuthenticatedUser());
                testRequestItem.setDateChanged(new Date());
                testRequestItem.setInitialSample(initialSample.get());
                testRequestItem.setReferralOutOrigin(null);
                testRequestItem.setReferralOutSample(null);

                Order order = testRequestItem.getOrder();
                order.getId();
                UpdateTestRequestItemInProgress(testRequestItem, order, initialSample.get().getAccessionNumber());
                orderService.updateOrderFulfillerStatus(order,
                        order.getFulfillerStatus(), order.getFulfillerComment(), order.getAccessionNumber());
            }
            dao.saveTestRequestItem(testRequestItem);
            for(Sample sample : testRequestSamples.getValue().getValue2().values()){
                updateSampleForTesting(sample);
            }
        }
        return instructionUpdate;
    }

    private Sample  updateSampleForTesting(Sample sample){
        if(sample.getStorageStatus() == null || sample.getStorageStatus() == StorageStatus.CHECKED_OUT) {
            sample.setStatus(SampleStatus.TESTING);
            sample.setChangedBy(Context.getAuthenticatedUser());
            sample.setDateChanged(new Date());
            return dao.saveSample(sample);
        }
        return sample;
    }

    public void updateOrderInstructions(Map<Integer, String> orderInstructions){
        if(orderInstructions == null || orderInstructions.isEmpty()) return;
        OrderService orderService = Context.getOrderService();
        for(Map.Entry<Integer,String> entry : orderInstructions.entrySet()){
            Order order = orderService.getOrder(entry.getKey());
            if(order != null) {
                dao.updateOrderInstructions(order, entry.getValue());
            }
        }
    }

    public Worksheet getWorksheetById(Integer id){
        return dao.getWorksheetById(id);
    }

    public Worksheet getWorksheetByUuid(String uuid){
        return dao.getWorksheetByUuid(uuid);
    }

    public Result<WorksheetDTO> findWorksheets(WorksheetSearchFilter filter){
        Result<WorksheetDTO> result = dao.findWorksheets(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy(),
                p.getResponsiblePersonId()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        Map<String, Object> requestContextItems = new HashMap<>();
        for (WorksheetDTO worksheetDTO : result.getData()) {
            worksheetDTO.setRequestContextItems(requestContextItems);
            if (worksheetDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(worksheetDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    worksheetDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    worksheetDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (worksheetDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(worksheetDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    worksheetDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    worksheetDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (worksheetDTO.getResponsiblePersonId() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(worksheetDTO.getResponsiblePersonId())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    worksheetDTO.setResponsiblePersonFamilyName(userPersonNameDTO.get().getFamilyName());
                    worksheetDTO.setResponsiblePersonGivenName(userPersonNameDTO.get().getGivenName());
                    worksheetDTO.setResponsiblePersonMiddleName(userPersonNameDTO.get().getMiddleName());
                }
            }
        }

        if(!result.getData().isEmpty() && filter.getIncludeWorksheetItems()){
            WorksheetItemSearchFilter itemSearchFilter=new WorksheetItemSearchFilter();
            itemSearchFilter.setWorksheetIds(result.getData().stream().map(p->p.getId()).collect(Collectors.toList()));
            if(!filter.getAllItems()){
                itemSearchFilter.setWorksheetItemStatuses(filter.getWorksheetItemStatuses());
                itemSearchFilter.setPatientId(filter.getPatientId());
                itemSearchFilter.setSampleRef(filter.getSampleRef());
                itemSearchFilter.setUrgency(filter.getUrgency());
                if(!filter.getTestConceptForWorksheetOnly()) {
                    itemSearchFilter.setTestConceptIds(filter.getTestConceptIds());
                }
            }
            itemSearchFilter.setIncludeTestResult(filter.getIncludeWorksheetItemTestResult());
            itemSearchFilter.setIncludeTestResultApprovals(filter.getIncludeTestItemTestResultApprovals());
            itemSearchFilter.setIncludeTestConcept(filter.getIncludeWorksheetItemConcept());
            itemSearchFilter.setVoided(false);
            itemSearchFilter.setIncludeTestResultId(filter.getIncludeTestResultIds());
            Result<WorksheetItemDTO> testItems = findWorksheetItems(itemSearchFilter);
            if(!testItems.getData().isEmpty()){
                Map<Integer, List<WorksheetItemDTO>> groups = testItems.getData().stream().collect(Collectors.groupingBy(WorksheetItemDTO::getWorksheetId));
                for(WorksheetDTO worksheetDTO : result.getData()){
                    List<WorksheetItemDTO> tests = groups.getOrDefault(worksheetDTO.getId(),null);
                    worksheetDTO.setWorksheetItems(tests == null ? new ArrayList<>() : tests);
                }
            }
        }


        return result;
    }

    public Worksheet saveWorksheet(Worksheet worksheet){
        return dao.saveWorksheet(worksheet);
    }

    public Worksheet saveWorksheet(WorksheetDTO worksheetDTO){
        Worksheet worksheet;
        boolean isNew = false;
        if (worksheetDTO.getUuid() == null) {
            isNew = true;
            worksheet = new Worksheet();
            worksheet.setCreator(Context.getAuthenticatedUser());
            worksheet.setDateCreated(new Date());


            worksheet.setStatus(WorksheetStatus.PENDING);

        } else {
            worksheet = dao.getWorksheetByUuid(worksheetDTO.getUuid());
            if (worksheet == null) {
                invalidRequest("labmanagement.thingnotexists", "Worksheet");
            }
            if(!WorksheetStatus.canEditWorksheet(worksheet.getStatus())){
                invalidRequest("labmanagement.worksheetnotmodifiable", "Worksheet can not be modified");
            }
            worksheet.setChangedBy(Context.getAuthenticatedUser());
            worksheet.setDateChanged(new Date());
        }

        if(StringUtils.isNotBlank(worksheetDTO.getAtLocationUuid())){
            Location atLocation = Context.getLocationService().getLocationByUuid(worksheetDTO.getAtLocationUuid());
            if(atLocation == null){
                invalidRequest("labmanagement.thingnotexists",  "Current user location");
            }
            worksheet.setAtLocation(atLocation);
        }else {
            invalidRequest("labmanagement.fieldrequired", "Diagonistic center");
        }

        if(worksheetDTO.getWorksheetDate() == null){
            invalidRequest("labmanagement.fieldrequired", "Worksheet Date");
        }else{

            worksheet.setWorksheetDate(worksheetDTO.getWorksheetDate());
        }

        if(StringUtils.isNotBlank(worksheetDTO.getRemarks())){
            worksheet.setRemarks(worksheetDTO.getRemarks());
        }else{
            worksheet.setRemarks(null);
        }

        if(StringUtils.isNotBlank(worksheetDTO.getTestUuid())){
            Concept concept = Context.getConceptService().getConceptByUuid(worksheetDTO.getTestUuid());
            if(concept == null){
                invalidRequest("labmanagement.thingnotexists",  "Test");
            }
            worksheet.setTest(concept);
        }else {
            worksheet.setTest(null);
        }

        if(StringUtils.isNotBlank(worksheetDTO.getDiagnosisTypeUuid())){
            Concept concept = Context.getConceptService().getConceptByUuid(worksheetDTO.getDiagnosisTypeUuid());
            if(concept == null){
                invalidRequest("labmanagement.thingnotexists",  "Diagnosis Type");
            }
            worksheet.setDiagnosisType(concept);
        }else {
            worksheet.setDiagnosisType(null);
        }

        if(StringUtils.isNotBlank(worksheetDTO.getResponsiblePersonUuid())){
            User user = Context.getUserService().getUserByUuid(worksheetDTO.getResponsiblePersonUuid());
            if(user == null){
                invalidRequest("labmanagement.thingnotexists",  "Responsible Person");
            }
            worksheet.setResponsiblePerson(user);
            worksheet.setResponsiblePersonOther(null);
        }else {
            if(StringUtils.isNotBlank(worksheetDTO.getResponsiblePersonOther())) {
                worksheet.setResponsiblePerson(null);
                worksheet.setResponsiblePersonOther(worksheetDTO.getResponsiblePersonOther());
            }else{
                invalidRequest("labmanagement.fieldrequired", "Responsible Person");
            }
        }

        if(worksheetDTO.getWorksheetItems() == null || worksheetDTO.getWorksheetItems().isEmpty()){
            invalidRequest("labmanagement.fieldrequired", "Worksheet Items");
        }

        List<WorksheetItem> currentWorksheetItems = isNew ? new ArrayList<>() : dao.getWorksheetItemsByWorksheetId(worksheet.getId());
        List<WorksheetItem> toDeleteWorksheetItems = currentWorksheetItems.stream().collect(Collectors.toList());
        List<WorksheetItem> toAddWorksheetItems = new ArrayList<>();
        int itemIndex = 0;
        for(WorksheetItemDTO worksheetItemDTO : worksheetDTO.getWorksheetItems()){
            itemIndex++;
            if(StringUtils.isBlank(worksheetItemDTO.getTestRequestItemSampleUuid())){
                invalidRequest("labmanagement.fieldrequired", "Item test request sample order " + itemIndex);
            }
            WorksheetItem existingItem = isNew ? null :
                    currentWorksheetItems.stream().filter(p->
                            p.getTestRequestItemSample().getUuid().equalsIgnoreCase(worksheetItemDTO.getTestRequestItemSampleUuid())).findFirst().orElse(null);
            if(existingItem == null){
                if(toAddWorksheetItems.stream().anyMatch(p->
                        p.getTestRequestItemSample().getUuid().equalsIgnoreCase(worksheetItemDTO.getTestRequestItemSampleUuid())
                        )){
                    continue;
                }
                TestRequestItemSample testRequestItemSample = dao.getTestRequestItemSampleByUuid(worksheetItemDTO.getTestRequestItemSampleUuid());
                if(testRequestItemSample == null || testRequestItemSample.getVoided()){
                    invalidRequest("labmanagement.thingnotexists",  "Item test request sample order " + itemIndex);
                }

                existingItem = new WorksheetItem();
                existingItem.setWorksheet(worksheet);
                existingItem.setTestRequestItemSample(testRequestItemSample);
                existingItem.setStatus(WorksheetItemStatus.PENDING);
                existingItem.setCreator(Context.getAuthenticatedUser());
                existingItem.setDateCreated(new Date());
                toAddWorksheetItems.add(existingItem);
            }else{
                toDeleteWorksheetItems.remove(existingItem);
            }

        }

        worksheet = dao.saveWorksheet(worksheet);
        if(StringUtils.isBlank(worksheet.getWorksheetNo())){
            worksheet.setWorksheetNo("WST-" + worksheet.getId());
            dao.saveWorksheet(worksheet);
        }
        for(WorksheetItem worksheetItem : toAddWorksheetItems){
            dao.saveWorksheetItem(worksheetItem);
        }

        if(!toDeleteWorksheetItems.isEmpty()){
            for(WorksheetItem worksheetItem : toDeleteWorksheetItems){
                List<TestResult> testResults = dao.getTestResultsByWorksheetItem(worksheetItem);
                if(!testResults.isEmpty()) {
                    toDeleteWorksheetItems.remove(worksheetItem);
                }
            }
            dao.deleteWorksheetItemsById(toDeleteWorksheetItems.stream().map(WorksheetItem::getId).collect(Collectors.toList()));
        }

        if(!isNew){
            checkWorksheetCompletion(worksheet);
        }

        return worksheet;
    }

    public Worksheet checkWorksheetCompletion(Worksheet worksheet){
        List<WorksheetItem> worksheetItems = dao.getWorksheetItemsByWorksheetId(worksheet.getId());
        int totalItems = 0;
        int cancelled = 0;
        int resulted = 0;
        int pending = 0;
        for (WorksheetItem p : worksheetItems) {
            totalItems++;
            switch (p.getStatus()) {
                case RESULTED:
                    resulted++;
                    break;
                case CANCELLED:
                    cancelled++;
                    break;
                case RESULTED_REJECTED:
                case PENDING:
                default:
                    pending++;
                    break;

            }
        }
        if (pending > 0) {
            if (!WorksheetStatus.PENDING.equals(worksheet.getStatus())) {
                worksheet.setStatus(WorksheetStatus.PENDING);
                worksheet = dao.saveWorksheet(worksheet);
            }
        } else if (resulted > 0) {
            if (!WorksheetStatus.RESULTED.equals(worksheet.getStatus())) {
                worksheet.setStatus(WorksheetStatus.RESULTED);
                worksheet = dao.saveWorksheet(worksheet);
            }
        } else if (cancelled > 0 || totalItems == 0) {
            if (!WorksheetStatus.CANCELLED.equals(worksheet.getStatus())) {
                worksheet.setStatus(WorksheetStatus.CANCELLED);
                worksheet = dao.saveWorksheet(worksheet);
            }
        }
        return worksheet;

    }

    public WorksheetItem getWorksheetItemById(Integer id){
        return dao.getWorksheetItemById(id);
    }

    public WorksheetItem getWorksheetItemByUuid(String uuid){
        return dao.getWorksheetItemByUuid(uuid);
    }

    public WorksheetItem saveWorksheetItem(WorksheetItem worksheetItem){
        return dao.saveWorksheetItem(worksheetItem);
    }

    public Result<WorksheetItemDTO> findWorksheetItems(WorksheetItemSearchFilter filter){
        Result<WorksheetItemDTO> result = dao.findWorksheetItems(filter);
        boolean setTestResults = filter.getIncludeTestResult();
        Map<Integer, List<TestResultDTO>> testResults = null;
        if(setTestResults){
            setTestResults = Context.getAuthenticatedUser().hasPrivilege(Privileges.APP_LABMANAGEMENT_TESTRESULTS);
            if(setTestResults) {
                TestResultSearchFilter testResultSearchFilter = new TestResultSearchFilter();
                testResultSearchFilter.setIncludeApprovals(filter.getIncludeTestResultApprovals());
                testResultSearchFilter.setWorksheetItemIds(result.getData().stream().map(p -> p.getId()).collect(Collectors.toList()));
                testResultSearchFilter.setVoided(false);
                testResults = findTestResults(testResultSearchFilter).getData().stream().collect(Collectors.groupingBy(TestResultDTO::getWorksheetItemId));
            }
        }

        Map<Integer, List<Concept>> concepts = null;
        if(filter.getIncludeTestConcept()){
                concepts = dao.getConceptsByIds(result.getData().stream().map(p->p.getTestId())
                        .distinct().collect(Collectors.toList())).stream()
                        .collect(Collectors.groupingBy(Concept::getConceptId));
        }

        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy(),
                p.getSampleCollectedBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        Map<String, Object> requestContextItems=new HashMap<>();
        for (WorksheetItemDTO worksheetItemDTO : result.getData()) {
            worksheetItemDTO.setRequestContextItems(requestContextItems);
            if(setTestResults){
                List<TestResultDTO> testResult = testResults.getOrDefault(worksheetItemDTO.getId(), null);
                if(testResult != null && !testResult.isEmpty()){
                    worksheetItemDTO.setTestResult(testResult.get(0));
                }
            }
            if(filter.getIncludeTestConcept()){
                List<Concept> concept = concepts.getOrDefault(worksheetItemDTO.getTestId(), null);
                if(concept != null && !concept.isEmpty()){
                    worksheetItemDTO.setTestConcept(concept.get(0));
                }
            }
            if (worksheetItemDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(worksheetItemDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    worksheetItemDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    worksheetItemDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (worksheetItemDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(worksheetItemDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    worksheetItemDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    worksheetItemDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (worksheetItemDTO.getSampleCollectedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(worksheetItemDTO.getSampleCollectedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    worksheetItemDTO.setSampleCollectedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    worksheetItemDTO.setSampleCollectedByMiddleName(userPersonNameDTO.get().getMiddleName());
                    worksheetItemDTO.setSampleCollectedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }
        return result;
    }

    public void deleteWorksheetItem(Integer workSheetItemId, String reason){
        if(StringUtils.isBlank(reason)){
            invalidRequest("labmanagement.fieldrequired", "Reason");
        }else if(reason.length() > 255){
            invalidRequest("labmanagement.thingnotexceeds", "Reason", "255");
        }

        WorksheetItem worksheetItem = dao.getWorksheetItemById(workSheetItemId);
        if(worksheetItem == null){
            invalidRequest("labmanagement.thingnotexists",  "Worksheet item");
        }

        if(worksheetItem.getStatus().equals(WorksheetItemStatus.RESULTED)){
            invalidRequest("labmanagement.thinginuse", "Worksheet item", "test results");
        }

        List<TestResult> testResults = dao.getTestResultsByWorksheetItem(worksheetItem);
        if(!testResults.isEmpty()){
            invalidRequest("labmanagement.thinginuse", "Worksheet item", "test results");
        }

        dao.deleteWorksheetItemsById(Arrays.asList(worksheetItem.getId()));
        checkWorksheetCompletion(worksheetItem.getWorksheet());
    }

    public void deleteWorksheet(Integer workSheetId, String reason){
        if(StringUtils.isBlank(reason)){
            invalidRequest("labmanagement.fieldrequired", "Reason");
        }else if(reason.length() > 255){
            invalidRequest("labmanagement.thingnotexceeds", "Reason", "255");
        }

        Worksheet worksheet = dao.getWorksheetById(workSheetId);
        if(worksheet == null){
            invalidRequest("labmanagement.thingnotexists",  "Worksheet");
        }

        if(worksheet.getStatus().equals(WorksheetStatus.RESULTED)){
            invalidRequest("labmanagement.thinginuse", "Worksheet item", "test results");
        }

        boolean hasTestResults = dao.worksheetHasTestResults(worksheet);
        if(hasTestResults){
            invalidRequest("labmanagement.thinginuse", "Worksheet item", "test results");
        }
        dao.deleteWorksheetItemsByWorksheetId(workSheetId);
        dao.deleteWorksheetById(workSheetId,reason, Context.getAuthenticatedUser().getId());

    }

    public void deleteLocation(String uuid) {
        try {
            deleteLocationInternal(uuid);
        } catch (Exception exception) {
            invalidRequest("labmanagement.thinginuse", "Location", "existing data");
        }
    }

    public void deleteLocationInternal(String uuid) {
        LocationService locationService = Context.getLocationService();
        Location location = locationService.getLocationByUuid(uuid);
        if (location == null) {
            invalidRequest("labmanagement.thingnotexists",  "Location");
        }

        if (location.getChildLocations() != null && location.getChildLocations().size() > 0) {
            invalidRequest("labmanagement.thinginuse", "Location", "child locations");
        }

        Set<LocationAttribute> locationAttributes = location.getAttributes();
        Set<LocationTag> locationTags = location.getTags();

        dao.deleteStockManagementParty(location);
        dao.deleteLocationTreeNodes(location.getId());

        if (locationTags != null) {
            locationTags.stream().collect(Collectors.toList()).forEach(p -> location.removeTag(p));
            locationService.saveLocation(location);
        }

        if (locationAttributes != null && !locationAttributes.isEmpty()) {
            dao.deleteLocationAttributes(locationAttributes.stream().map(p -> p.getLocationAttributeId()).collect(Collectors.toList()));
        }

        dao.deleteLocation(location.getId());
    }

    public Result<TestResultDTO> findTestResults(TestResultSearchFilter filter){
        Result<TestResultDTO> result = dao.findTestResults(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy(),
                p.getResultBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        List<Integer> observationIds = result.getData().stream().map(p-> p.getObsId()).collect(Collectors.toList());
        Map<Integer, List<Obs>> observations = dao.getObsByIds(observationIds).stream().collect(Collectors.groupingBy(p-> p.getObsId()));

        Map<Integer, List<TestApprovalDTO>> approvals = null;
        if(filter.getIncludeApprovals() && !result.getData().isEmpty()){
            approvals = dao.getTestApprovals(result.getData().stream().map(p->p.getId()).collect(Collectors.toList()))
                    .stream().collect(Collectors.groupingBy(TestApprovalDTO::getTestResultId));

        }

        User currentUser = null;
        Map<Integer, List<TestApprovalDTO>> previousApprovals = null;
        if(filter.getPermApproval() && !result.getData().isEmpty()){
            if(approvals != null)
                previousApprovals = approvals;
            else {
                if (result.getData().stream().anyMatch(p -> (p.getApprovalFlowLevelTwoAllowPrevious() != null && !p.getApprovalFlowLevelTwoAllowPrevious()) ||
                        (p.getApprovalFlowLevelThreeAllowPrevious() != null && !p.getApprovalFlowLevelThreeAllowPrevious()) ||
                        (p.getApprovalFlowLevelFourAllowPrevious() != null && !p.getApprovalFlowLevelFourAllowPrevious()))) {
                    previousApprovals = dao.getPreviousTestApprovalRefs(
                            result.getData().stream()
                                    .filter(p -> p.getCurrentApprovalLevel() > 1)
                                    .map(p -> p.getId()).collect(Collectors.toList())).stream().collect(Collectors.groupingBy(TestApprovalDTO::getTestResultId));
                } else {
                    previousApprovals = new HashMap<>();
                }
            }
            currentUser = Context.getAuthenticatedUser();
        }




        int editTimeout = GlobalProperties.getTestResultEditTimeout();
        boolean hasTestResultPerm = currentUser != null && currentUser.hasPrivilege(Privileges.TASK_LABMANAGEMENT_TESTRESULTS_MUTATE);

        Map<String, Object> requestContextItems=new HashMap<>();
        for (TestResultDTO testResultDTO : result.getData()) {
            testResultDTO.setRequestContextItems(requestContextItems);
            List<Obs> obs = observations.getOrDefault(testResultDTO.getObsId(), null);
            testResultDTO.setObs(obs != null && obs.size() > 0 ? obs.get(0): null);
            if (testResultDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testResultDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testResultDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    testResultDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testResultDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testResultDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testResultDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testResultDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testResultDTO.getResultBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testResultDTO.getResultBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testResultDTO.setResultByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testResultDTO.setResultByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testResultDTO.setResultByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }

            if(filter.getIncludeApprovals() && approvals != null){
                testResultDTO.setApprovals(approvals.getOrDefault(testResultDTO.getId(),new ArrayList<>()));
            }

            if(filter.getPermApproval()){
                if(currentUser != null){
                    testResultDTO.setCanApprove(TestResultDTO.canApproveTestResults(testResultDTO,currentUser,
                            previousApprovals.getOrDefault(testResultDTO.getId(), null)));
                }
            }

            testResultDTO.setCanUpdate(hasTestResultPerm && TestResultDTO.canUpdateTestResult(testResultDTO, editTimeout));
        }

        return result;
    }

    private void setNextTestApproval(TestResult testResult, ApprovalFlow approvalFlow, boolean reApprove){
        if(testResult.getCompleted() || testResult.getRequireApproval() == null || !testResult.getRequireApproval()) return;
        if(testResult.getCurrentApproval() == null){
            approvalFlow.setNextTestApproval(testResult, null,0);
        }
        else{
            TestApproval currentApproval = testResult.getCurrentApproval();
            // As long as current approval, is not yet approved, do nothing
            if(currentApproval.getApprovalResult() == null){
                return;
            }

            if(currentApproval.getApprovalResult().equals(ApprovalResult.APPROVED)){
                approvalFlow.setNextTestApproval(testResult, currentApproval,currentApproval.getCurrentApprovalLevel());
            }else if(reApprove){
                approvalFlow.setNextTestApproval(testResult, currentApproval,currentApproval.getCurrentApprovalLevel() - 1);
            }
        }
    }

    public List<TestResult> saveWorksheetTestResults(WorksheetTestResultDTO worksheetTestResultDTO){
        if(worksheetTestResultDTO == null) return new ArrayList<>();
        if(worksheetTestResultDTO.getTestResults() == null || worksheetTestResultDTO.getTestResults().isEmpty())
            return new ArrayList<>();

        if(StringUtils.isBlank(worksheetTestResultDTO.getWorksheetUuid())){
            invalidRequest("labmanagement.fieldrequired", "Worksheet identifier");
        }

        Worksheet worksheet = getWorksheetByUuid(worksheetTestResultDTO.getWorksheetUuid());
        if(worksheet == null){
            invalidRequest("labmanagement.thingnotexists", "Worksheet");
        }

        List<TestResult> testResults = new ArrayList<>();
        LabManagementService labManagementService = Context.getService(LabManagementService.class);
        for(TestResultDTO testResultDTO : worksheetTestResultDTO.getTestResults()){
             testResults.add(labManagementService.saveTestResult(testResultDTO, worksheet));
        }
        return testResults;
    }

    public List<Integer> getWorksheetTestResultIdsForAttachmentUpdate(Integer worksheetId){
        return dao.getWorksheetTestResultIdsForAttachmentUpdate(worksheetId);
    }


    public void saveTestResultAttachment(List<Integer> testResultIds, Document document){
        dao.saveTestResultAttachment(testResultIds, document);
    }

    public TestResult saveTestResult(TestResultDTO testResultDTO){
        return saveTestResult(testResultDTO, null);
    }

    public TestResult saveTestResult(TestResultDTO testResultDTO, Worksheet worksheetToValidateAgainst){
        TestResult testResult;
        boolean isNew = false;
        boolean isTestAlreadyCompleted = false;

        if(StringUtils.isBlank(testResultDTO.getWorksheetItemUuid()) && StringUtils.isBlank(testResultDTO.getTestRequestItemSampleUuid())){
            invalidRequest("labmanagement.fieldrequired", "Worksheet Item or Test request item sample");
        }

        List<TestResult> testResults = null;
        if(StringUtils.isNotBlank(testResultDTO.getWorksheetItemUuid())) {
            testResults = dao.getTestResultByWorksheetItem(testResultDTO.getWorksheetItemUuid(), false);
        } else if(StringUtils.isNotBlank(testResultDTO.getTestRequestItemSampleUuid())){
            testResults = dao.getTestResultByTestRequestItemSample(testResultDTO.getTestRequestItemSampleUuid(), false);
        }
        if(testResults != null && !testResults.isEmpty()){
            if(testResults.size() > 1){
                TestResult latestTestResult = testResults.stream().sorted((x,y)-> x.getId().compareTo(y.getId())).findFirst().orElse(null);
                for(TestResult oldTestResults : testResults){
                    if(!oldTestResults.getId().equals(latestTestResult.getId())){
                        oldTestResults.setVoided(true);
                        dao.saveTestResult(oldTestResults);
                    }
                }
                testResultDTO.setUuid(latestTestResult.getUuid());
            }else{
                testResultDTO.setUuid(testResults.get(0).getUuid());
            }
        }

        if (testResultDTO.getUuid() == null) {
            isNew = true;
            testResult = new TestResult();
            testResult.setCreator(Context.getAuthenticatedUser());
            testResult.setDateCreated(new Date());

            if(testResultDTO.getObs() == null){
                invalidRequest("labmanagement.fieldrequired", "Result observations");
            }

            if(StringUtils.isBlank(testResultDTO.getAtLocationUuid())){
                invalidRequest("labmanagement.fieldrequired", "Current user location");
            }

            Location atLocation = Context.getLocationService().getLocationByUuid(testResultDTO.getAtLocationUuid());
            if(atLocation == null){
                invalidRequest("labmanagement.thingnotexists",  "Current user location");
            }
            testResult.setAtLocation(atLocation);

        } else {
            testResult = dao.getTestResultByUuid(testResultDTO.getUuid());
            if (testResult == null) {
                invalidRequest("labmanagement.thingnotexists", "Test result");
            }
            testResult.setChangedBy(Context.getAuthenticatedUser());
            testResult.setDateChanged(new Date());

            int editTimeout = GlobalProperties.getTestResultEditTimeout();
            if(!TestResultDTO.canUpdateTestResult(testResult,editTimeout)){
                invalidRequest("labmanagement.testresulteditnotallowed");
            }
            isTestAlreadyCompleted = testResult.getCompleted();
        }

        testResult.setResultDate(new Date());
        testResult.setResultBy(Context.getAuthenticatedUser());



        if(isNew){

            if(StringUtils.isNotBlank(testResultDTO.getWorksheetItemUuid())) {
                WorksheetItem worksheetItem = dao.getWorksheetItemByUuid(testResultDTO.getWorksheetItemUuid());
                if(worksheetItem == null || worksheetItem.getVoided()){
                    invalidRequest("labmanagement.thingnotexists", "Worksheet Item");
                }
                testResult.setWorksheetItem(worksheetItem);
                testResult.setTestRequestItemSample(worksheetItem.getTestRequestItemSample());
                worksheetItem.setStatus(WorksheetItemStatus.RESULTED);
                testResult.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
            }else if(StringUtils.isNotBlank(testResultDTO.getTestRequestItemSampleUuid())){
                TestRequestItemSample testRequestItemSample = dao.getTestRequestItemSampleByUuid(testResultDTO.getTestRequestItemSampleUuid());
                if(testRequestItemSample == null || testRequestItemSample.getVoided()){
                    invalidRequest("labmanagement.thingnotexists", "Test request item sample");
                }
                List<WorksheetItem> worksheetItem = dao.getWorksheetItemsByTestRequestItemSampleUuid(testRequestItemSample.getUuid(), false);
                if(!worksheetItem.isEmpty()){
                    testResult.setWorksheetItem(
                            worksheetItem.stream().filter(p -> WorksheetItemStatus.canRegisterTestResults(p.getStatus())).min((x, y) -> y.getId().compareTo(x.getId())).orElse(null)
                    );
                }
                testResult.setTestRequestItemSample(testRequestItemSample);
                testResult.setOrder(testRequestItemSample.getTestRequestItem().getOrder());
            }
        }
        boolean recreateNextApprovalIfObsChange = false;
        String oldObs = null;
        String oldRemarks = testResult.getRemarks();
        if(testResult.getObs() != null)
            oldObs = testResult.getObs().getValueAsString(Context.getLocale());

        if(testResultDTO.getObs() != null){
            Order order = testResult.getTestRequestItemSample().getTestRequestItem().getOrder();
            if(testResultDTO.getObs().getOrder() == null){
                invalidRequest("labmanagement.fieldrequired", "Observation order");
            }
            if(!testResultDTO.getObs().getOrder().getId().equals(order.getId())){
                invalidRequest("labmanagement.thingnotexists", "Order for observation");
            }
        }else{
            invalidRequest("labmanagement.fieldrequired", "Observations");
        }

        TestConfig testConfig = dao.getTestConfigByConcept(testResult.getOrder().getConcept().getConceptId());
        if(testConfig == null){
            invalidRequest("labmanagement.thingnotexists", "Test config for test");
        }

        TestApproval oldTestApproval = testResult.getCurrentApproval();
        testResult.setRequireApproval(testConfig.getRequireApproval() != null && testConfig.getRequireApproval());
        boolean referredOut = testResult.getTestRequestItemSample().getTestRequestItem().getReferredOut() != null &&
                testResult.getTestRequestItemSample().getTestRequestItem().getReferredOut();
        if(referredOut){
            testResult.setRequireApproval(false);
        }

        if(testResult.getRequireApproval()) {
            if (testConfig.getApprovalFlow() == null) {
                invalidRequest("labmanagement.thingnotexists", "Test config approval flow for test");
            }
            if(isNew && testConfig.getApprovalFlow().getVoided()){
                invalidRequest("labmanagement.thingnotexists", "Test config approval flow voided for test");
            }

            if(testResult.getCurrentApproval() == null){
                setNextTestApproval(testResult, testConfig.getApprovalFlow(), false);
            }else if(testResult.getCompleted() != null  &&
                    !testResult.getCompleted() &&
                    testResult.getCurrentApproval().getApprovalResult() != null ){
                if(testResult.getCurrentApproval().getApprovalResult().equals(ApprovalResult.NOT_REQUIRED)) {
                    testResult.getCurrentApproval().setApprovalResult(null);
                    testResult.setStatus(testResult.getCurrentApproval().getApprovalConfig().getApprovedStatus());
                }else if(testResult.getCurrentApproval().getApprovalResult().equals(ApprovalResult.REJECTED) ||
                        testResult.getCurrentApproval().getApprovalResult().equals(ApprovalResult.RETURNED)) {
                    recreateNextApprovalIfObsChange = true;
                }
            }
        } else{
            if(testResult.getCurrentApproval() != null){
                TestApproval testApproval = testResult.getCurrentApproval();
                if(testApproval.getApprovalResult() == null) {
                    testApproval.setApprovalResult(ApprovalResult.NOT_REQUIRED);
                }
            }
            testResult.setStatus("Approval Not Required");
            if(testResult.getCompleted() == null || !testResult.getCompleted()){
                testResult.setCompleted(true);
                testResult.setCompletedResult(true);
                testResult.setCompletedDate(new Date());
            }
        }

        if(StringUtils.isNotBlank(testResultDTO.getRemarks())){
            if(testResultDTO.getRemarks().length() > 1000) {
                invalidRequest("labmanagement.thingnotexceeds", "Remarks", "1000");
            }
            testResult.setRemarks(testResultDTO.getRemarks());
        }

        if(!isNew && testResult.getObs() != null){
            User currentUser = Context.getAuthenticatedUser();
            Obs oldObs1 = testResult.getObs();
            oldObs1.setVoided(true);
            oldObs1.setVoidedBy(currentUser);
            oldObs1.setDateVoided(new Date());
            if(oldObs1.hasGroupMembers()){
                for(Obs oldObsMember : oldObs1.getGroupMembers()){
                    oldObsMember.setVoided(true);
                    oldObsMember.setVoidedBy(currentUser);
                    oldObsMember.setDateVoided(new Date());
                    Context.getObsService().voidObs(oldObsMember, "New Test Results");
                }
            }
            Context.getObsService().voidObs(oldObs1, "New Test Results");
        }

        String newObs = null;
        if(testResultDTO.getObs() != null){
            Encounter encounter = testResult.getTestRequestItemSample().getTestRequestItem().getEncounter();
            if(encounter != null){
                testResultDTO.getObs().setEncounter(encounter);
                encounter.addObs(testResultDTO.getObs());
            }else{
                testResultDTO.getObs().setPerson( testResult.getTestRequestItemSample().getTestRequestItem().getOrder().getPatient().getPerson());
                testResultDTO.getObs().setObsDatetime(new Date());
            }

            Obs obs = Context.getObsService().saveObs(testResultDTO.getObs(), "Updated Test Result");
            if(recreateNextApprovalIfObsChange){
                newObs = obs.getValueAsString(Context.getLocale());
            }
            testResult.setObs(obs);
        }

        if(recreateNextApprovalIfObsChange && (!Objects.equals(newObs,oldObs) || !Objects.equals(testResult.getRemarks(), oldRemarks) )){
            setNextTestApproval(testResult, testConfig.getApprovalFlow(), true);
        }

        testResult = dao.saveTestResult(testResult);

        if(testResult.getCurrentApproval() != null){
            dao.saveTestApproval(testResult.getCurrentApproval());
        }
        if(oldTestApproval != null){
            dao.saveTestApproval(oldTestApproval);
        }

        if(testResult.getWorksheetItem() != null) {
            if (!WorksheetItemStatus.RESULTED.equals(testResult.getWorksheetItem().getStatus())) {
                testResult.getWorksheetItem().setStatus(WorksheetItemStatus.RESULTED);
                dao.saveWorksheetItem(testResult.getWorksheetItem());
            }
        }

        if(!isTestAlreadyCompleted && testResult.getCompleted()){
            onTestResultCompleted(testResult);
        }

        if(testResult.getTestRequestItemSample().getTestRequestItem().getFinalResult() == null ||
        !testResult.getTestRequestItemSample().getTestRequestItem().getFinalResult().equals(testResult.getId())){
            testResult.getTestRequestItemSample().getTestRequestItem().setFinalResult(testResult);
            testResult.getTestRequestItemSample().getTestRequestItem().setResultDate(testResult.getResultDate());
            dao.saveTestRequestItem(testResult.getTestRequestItemSample().getTestRequestItem());
        }

        if(testResult.getCompleted()){
            checkCompletion(testResult.getTestRequestItemSample().getTestRequestItem().getTestRequest());
        }

        if(testResult.getWorksheetItem() != null) {
            checkWorksheetCompletion(testResult.getWorksheetItem().getWorksheet());
        }
        return testResult;
    }

    public TestResult getTestResultById(Integer id){
        return dao.getTestResultById(id);
    }

    public TestResult getTestResultByUuid(String uuid){
        return dao.getTestResultByUuid(uuid);
    }

    public List<TestResult> getTestResultByWorksheetItem(String worksheetItemUuid){
        return dao.getTestResultByWorksheetItem(worksheetItemUuid, false);
    }

    public List<TestResult> getTestResultByTestRequestItemSample(String testRequestItemSampleUuid){
        return dao.getTestResultByTestRequestItemSample(testRequestItemSampleUuid, false);
    }


    public ApprovalDTO approveTestResultItem(TestRequestAction testRequestAction){
        if(testRequestAction == null){
            invalidRequest("labmanagement.fieldrequired", "Approval information");
        }

        if(testRequestAction.getRecords() == null || testRequestAction.getRecords().isEmpty()){
            invalidRequest("labmanagement.fieldrequired", "Records");
        }

        if(testRequestAction.getRecords().size() > 100){
            invalidRequest("labmanagement.fieldrequired", "Records not exceeding 100");
        }

        if(testRequestAction.getAction() == null){
            invalidRequest("labmanagement.fieldrequired", "Action");
        }

        if(testRequestAction.getAction().equals(ApprovalResult.RETURNED)){
            invalidRequest("labmanagement.testrequestreturnnotsupported");
        }

        if(testRequestAction.getAction().equals(ApprovalResult.REJECTED) && StringUtils.isBlank(testRequestAction.getRemarks())){
            invalidRequest("labmanagement.fieldrequired", "Rejection remarks");
        }

        if(!StringUtils.isBlank(testRequestAction.getRemarks()) && testRequestAction.getRemarks().length() > 500){
            invalidRequest("labmanagement.thingnotexceeds", "Name", "500");
        }

        List<String> records = testRequestAction.getRecords().stream().distinct().collect(Collectors.toList());
        List<TestResult> testResults = dao.getTestResultsByUuid(testRequestAction.getRecords(), false);
        if(testResults.size() != records.size()){
            invalidRequest("labmanagement.notexists",
                    String.format("%1s Record(s)", records.size() - testResults.size()));
        }


        User currentUser = Context.getAuthenticatedUser();
        Map<Integer, List<TestApprovalDTO>>  previousApprovals = dao.getPreviousTestApprovalRefs(
                testResults.stream()
                        .filter(p->p.getCurrentApproval().getCurrentApprovalLevel() > 1)
                        .map(p->p.getId()).collect(Collectors.toList())).stream().collect(Collectors.groupingBy(TestApprovalDTO::getTestResultId));

        for(TestResult testResult : testResults){
            if(!testResult.getRequireApproval()){
                invalidRequest("labmanagement.approvalnotrequired", testResult.getOrder().getOrderNumber());
            }

            if(!TestResultDTO.canApproveTestResults(testResult, currentUser, previousApprovals.getOrDefault(testResult.getId(), null))) {
                invalidRequest("labmanagement.approvalnotallowed", testResult.getOrder().getOrderNumber());
            }
        }

        String remarks = StringUtils.isBlank(testRequestAction.getRemarks()) ? null : testRequestAction.getRemarks().trim();
        Map<Integer, Worksheet> worksheetsToUpdate = new HashMap<>();
        for(TestResult testResult : testResults){
            TestApproval oldApproval = testResult.getCurrentApproval();
            oldApproval.setApprovedBy(currentUser);
            oldApproval.setApprovalDate(new Date());
            oldApproval.setRemarks(remarks);

            if(testRequestAction.getAction().equals(ApprovalResult.APPROVED)) {
                oldApproval.setApprovalResult(ApprovalResult.APPROVED);
                setNextTestApproval(testResult, testResult.getCurrentApproval().getApprovalFlow(),false);
                if(testResult.getCompleted()){
                    onTestResultCompleted(testResult);
                }
            }else{
                oldApproval.setApprovalResult(ApprovalResult.REJECTED);
                testResult.setStatus(testResult.getCurrentApproval().getApprovalConfig().getRejectedStatus());
                if(testResult.getWorksheetItem() != null){
                    WorksheetItem worksheetItemToUpdate = testResult.getWorksheetItem();
                    worksheetItemToUpdate.setStatus(WorksheetItemStatus.RESULTED_REJECTED);
                    dao.saveWorksheetItem(worksheetItemToUpdate);
                    worksheetsToUpdate.putIfAbsent(worksheetItemToUpdate.getWorksheet().getId(), worksheetItemToUpdate.getWorksheet());
                }
            }
            dao.saveTestApproval(oldApproval);
            if(testResult.getCurrentApproval() != null && (testResult.getCurrentApproval().getId() == null || !oldApproval.getId().equals(testResult.getCurrentApproval().getId()))){
                dao.saveTestApproval(testResult.getCurrentApproval());
            }
            dao.saveTestResult(testResult);
        }

        for(Map.Entry<String, List<TestResult>> testRequestGroup :
                testResults.stream().collect(Collectors.groupingBy(x->x.getTestRequestItemSample().getTestRequestItem().getTestRequest().getUuid())).entrySet()){
            if(!testRequestAction.getAction().equals(ApprovalResult.APPROVED)){
                for(TestResult testResult : testRequestGroup.getValue()){
                    testResult.getTestRequestItemSample().getTestRequestItem().setReturnCount(
                            (testResult.getTestRequestItemSample().getTestRequestItem().getReturnCount() == null ? 0
                            : testResult.getTestRequestItemSample().getTestRequestItem().getReturnCount()) + 1
                    );
                    dao.saveTestRequestItem(testResult.getTestRequestItemSample().getTestRequestItem());
                }
            }
            checkCompletion(testRequestGroup.getValue().get(0).getTestRequestItemSample().getTestRequestItem().getTestRequest());
        }

        if(!worksheetsToUpdate.isEmpty()){
            for (Map.Entry<Integer, Worksheet> worksheet : worksheetsToUpdate.entrySet()){
                checkWorksheetCompletion(worksheet.getValue());
            }
        }

        ApprovalDTO approvalDTO=new ApprovalDTO();
        approvalDTO.setRemarks(remarks);
        approvalDTO.setResult(testRequestAction.getAction());
        return approvalDTO;
    }

    private Order onTestResultCompleted(TestResult testResult){
        TestRequestItem testRequestItem = testResult.getTestRequestItemSample().getTestRequestItem();
        testRequestItem.setStatus(TestRequestItemStatus.COMPLETED);
        testRequestItem.setCompleted(true);
        boolean sendAlert = testRequestItem.getCompletedDate() == null && testRequestItem.getDateCreated().after(DateUtils.addMinutes(new Date(),-1 * GlobalProperties.getTestResultNotificationTimeout()));
        testRequestItem.setCompletedDate(new Date());
        dao.saveTestRequestItem(testRequestItem);
        Order toReturn = null;
        if(testResult.getOrder() != null && ! Order.FulfillerStatus.COMPLETED.equals(testResult.getOrder().getFulfillerStatus())){
            toReturn = Context.getOrderService().updateOrderFulfillerStatus(testResult.getOrder(),
                    Order.FulfillerStatus.COMPLETED,testResult.getOrder().getFulfillerComment());
        }
        if(sendAlert && testRequestItem.getTestRequest().getReferredIn() != null && !testRequestItem.getTestRequest().getReferredIn()) {
            Provider provider = testRequestItem.getTestRequest().getProvider();
            List<User> usersToNotify = new ArrayList<>();
            usersToNotify.add(testRequestItem.getCreator());
            if (provider != null && provider.getPerson() != null) {
                List<User> providerUser = Context.getUserService().getUsersByPerson(provider.getPerson(), false);
                if (providerUser != null && !providerUser.isEmpty() && !usersToNotify.get(0).getUserId().equals(providerUser.get(0).getUserId())) {
                    usersToNotify.add(providerUser.get(0));
                }
            }

            Patient patient = testRequestItem.getTestRequest().getPatient();
            String name = patient != null ? patient.getPatientIdentifier().getIdentifier() + " - " + patient.getPersonName().getFullName() : "Unknown";
            Alert alert = new Alert(String.format("%1s results are ready for %2s", testResult.getObs().getConcept().getDisplayString(), name), usersToNotify);
            alert.setDateToExpire(DateUtils.addDays(new Date(), 1));
            Context.getAlertService().saveAlert(alert);
        }
        return toReturn;
    }

    public Result<TestApprovalDTO> findTestApprovals(TestApprovalSearchFilter filter){
        Result<TestApprovalDTO> result = dao.findTestApprovals(filter);
        if(!result.getData().isEmpty()) {
            List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                    p.getApprovedBy()
            )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
            for (TestApprovalDTO testApprovalDTO : result.getData()) {
                if (testApprovalDTO.getApprovedBy() != null) {
                    Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testApprovalDTO.getApprovedBy())).findFirst();
                    if (userPersonNameDTO.isPresent()) {
                        testApprovalDTO.setApprovedByFamilyName(userPersonNameDTO.get().getFamilyName());
                        testApprovalDTO.setApprovedByMiddleName(userPersonNameDTO.get().getMiddleName());
                        testApprovalDTO.setApprovedByGivenName(userPersonNameDTO.get().getGivenName());
                    }
                }
            }
        }
        return result;
    }

    public DashboardMetricsDTO getDashboardMetrics(Date startDate, Date endDate){
        return dao.getDashboardMetrics(startDate, endDate);
    }


    public BatchJobDTO saveBatchJob(BatchJobDTO batchJobDTO) {
        Location locationScope = null;
        if(!StringUtils.isBlank(batchJobDTO.getLocationScope())){
            locationScope = Context.getLocationService().getLocationByUuid(batchJobDTO.getLocationScope());
        }
        BatchJobSearchFilter batchJobSearchFilter=new BatchJobSearchFilter();
        batchJobSearchFilter.setBatchJobTypes( Arrays.asList(batchJobDTO.getBatchJobType()));

        Map<?,?> hashMap = batchJobDTO.getReportParameters() == null ? null :
                batchJobDTO.getReportParameters().entrySet().stream().collect(
                        Collectors.toMap(Map.Entry::getKey, p -> p.getValue() == null ? new LinkedHashMap<String, Object>() : p.getValue().toMap()));
        if(hashMap != null){
            if(batchJobDTO.getParametersMap() != null) {
                hashMap = MapUtil.deepMerge(batchJobDTO.getParametersMap(), hashMap, 5);
            }
        }else{
            hashMap = batchJobDTO.getParametersMap();
        }
        batchJobDTO.setParameters( hashMap == null ? batchJobDTO.getParameters() : GenericObject.toJson(hashMap));

        batchJobSearchFilter.setParameters(batchJobDTO.getParameters());
        batchJobSearchFilter.setPrivilegeScope(batchJobDTO.getPrivilegeScope());
        batchJobSearchFilter.setBatchJobStatus(Arrays.asList(BatchJobStatus.Pending, BatchJobStatus.Running));
        Result<BatchJobDTO> pendingSimilarJobs = findBatchJobs(batchJobSearchFilter);
        BatchJob batchJob = null;
        if(!pendingSimilarJobs.getData().isEmpty()){
            batchJob = dao.getBatchJobById(pendingSimilarJobs.getData().get(0).getId());
            if(batchJob.getBatchJobOwners() == null){
                batchJob.setBatchJobOwners(new HashSet<>());
            }
            Integer currentUserId = Context.getAuthenticatedUser().getId();
            if(!batchJob.getBatchJobOwners().stream().anyMatch(p->p.getOwner().getId().equals(currentUserId))){
                BatchJobOwner batchJobOwner = new BatchJobOwner();
                batchJobOwner.setOwner(Context.getAuthenticatedUser());
                batchJob.addBatchJobOwner(batchJobOwner);
            }
        }
        else {

            batchJob = new BatchJob();
            batchJob.setCreator(Context.getAuthenticatedUser());
            batchJob.setDateCreated(new Date());
            batchJob.setBatchJobType(batchJobDTO.getBatchJobType());
            batchJob.setStatus(BatchJobStatus.Pending);
            batchJob.setDescription(batchJobDTO.getDescription());
            batchJob.setExpiration(DateUtils.addMinutes(new Date(), GlobalProperties.getBatchJobExpiryInMinutes()));
            batchJob.setParameters(batchJobDTO.getParameters());
            batchJob.setPrivilegeScope(batchJobDTO.getPrivilegeScope());
            if (locationScope != null) {
                batchJob.setLocationScope(locationScope);
            }

            BatchJobOwner batchJobOwner = new BatchJobOwner();
            batchJobOwner.setOwner(Context.getAuthenticatedUser());
            batchJob.addBatchJobOwner(batchJobOwner);
        }

        dao.saveBatchJob(batchJob);
        AsyncTasksBatchJob.queueBatchJob(batchJob);

        batchJobSearchFilter = new BatchJobSearchFilter();
        batchJobSearchFilter.setBatchJobUuids(Arrays.asList(batchJob.getUuid()));
        Result<BatchJobDTO> jobs = findBatchJobs(batchJobSearchFilter);
        return jobs.getData().isEmpty() ? null : jobs.getData().get(0);
    }

    public Result<BatchJobDTO> findBatchJobs(BatchJobSearchFilter filter){
        return dao.findBatchJobs(filter);
    }

    public void failBatchJob(String batchJobUuid, String reason){
        BatchJob batchJob = dao.getBatchJobByUuid(batchJobUuid);
        if(batchJob == null) return;

        if(!batchJob.getStatus().equals(BatchJobStatus.Running) && !batchJob.getStatus().equals(BatchJobStatus.Pending)){
            invalidRequest("labmanagement.batchjob.notcancellable");
        }
        if(reason != null && reason.length() > 2500){
            reason = reason.substring(0,2500-1);
        }
        batchJob.setExitMessage(reason);
        batchJob.setStatus(BatchJobStatus.Failed);
        batchJob.setDateChanged(new Date());
        batchJob.setChangedBy(Context.getAuthenticatedUser());
        dao.saveBatchJob(batchJob);
    }


    public void cancelBatchJob(String batchJobUuid, String reason){
        BatchJob batchJob = dao.getBatchJobByUuid(batchJobUuid);
        if(batchJob == null) return;

        if(!batchJob.getStatus().equals(BatchJobStatus.Running) && !batchJob.getStatus().equals(BatchJobStatus.Pending)){
            invalidRequest("labmanagement.batchjob.notcancellable");
        }

        batchJob.setStatus(BatchJobStatus.Cancelled);
        batchJob.setCancelReason(reason);
        batchJob.setCancelledBy(Context.getAuthenticatedUser());
        batchJob.setCancelledDate(new Date());
        batchJob.setDateChanged(new Date());
        batchJob.setChangedBy(Context.getAuthenticatedUser());
        dao.saveBatchJob(batchJob);

        AsyncTasksBatchJob.stopBatchJob(batchJob);
    }

    public List<Report> getReports(){
        return Report.getAllReports();
    }

    public BatchJob getNextActiveBatchJob(){
        return dao.getNextActiveBatchJob();
    }

    public BatchJob getBatchJobByUuid(String batchJobUuid){
        return dao.getBatchJobByUuid(batchJobUuid);
    }

    public void saveBatchJob(BatchJob batchJob){
        dao.saveBatchJob(batchJob);
    }


    public void updateBatchJobRunning(String batchJobUuid){
        BatchJob batchJob = dao.getBatchJobByUuid(batchJobUuid);
        if(batchJob == null) return;
        batchJob.setStatus(BatchJobStatus.Running);
        if(batchJob.getStartTime() == null) {
            batchJob.setStartTime(new Date());
        }
        batchJob.setDateChanged(new Date());
        batchJob.setChangedBy(Context.getAuthenticatedUser());
        dao.saveBatchJob(batchJob);
    }

    public void expireBatchJob(String batchJobUuid, String reason){
        BatchJob batchJob = dao.getBatchJobByUuid(batchJobUuid);
        if(batchJob == null) return;
        batchJob.setStatus(BatchJobStatus.Expired);
        if(batchJob.getStartTime() != null){
            batchJob.setEndTime(new Date());
        }
        batchJob.setExitMessage(reason);
        batchJob.setDateChanged(new Date());
        batchJob.setChangedBy(Context.getAuthenticatedUser());
        dao.saveBatchJob(batchJob);
    }

    public void updateBatchJobExecutionState(String batchJobUuid, String  executionState){
        BatchJob batchJob = dao.getBatchJobByUuid(batchJobUuid);
        if(batchJob == null) return;
        batchJob.setExecutionState(executionState);
        batchJob.setDateChanged(new Date());
        batchJob.setChangedBy(Context.getAuthenticatedUser());
        dao.saveBatchJob(batchJob);
    }

    public List<BatchJob> getExpiredBatchJobs(){
        return dao.getExpiredBatchJobs();
    }

    public void deleteBatchJob(BatchJob batchJob){
        dao.deleteBatchJob(batchJob);
    }

    public List<Order> getOrdersToMigrate(Integer laboratoryEncounterTypeId, Integer afterOrderId, int limit, Date startDate, Date endDate){
        return dao.getOrdersToMigrate(laboratoryEncounterTypeId,afterOrderId,limit,startDate,endDate);
    }

    public Pair<Boolean, String> migrateOrder(Order orderToMigration){

        Order order = Context.getOrderService().getOrder(orderToMigration.getId());

        Order nextOrder = dao.getNextOrder(order);
        if(nextOrder != null){
            return new Pair<>(false, String.format("Order is superceeded by order %1s  with id %2s", nextOrder.getOrderNumber() , nextOrder.getOrderId()));
        }

        List<Order> previousOrders = new ArrayList<>();
        Order previousOrder = order.getPreviousOrder();
        int max=20;
        while(previousOrder != null && max > 0){
            previousOrders.add(previousOrder);
            previousOrder = previousOrder.getPreviousOrder();
            max--;
        }

        Integer orderWithTestRequestItem = dao.getOrderInChainWithTestRequestItem(order, previousOrders);
        if(orderWithTestRequestItem != null){
            return new Pair<>(false, String.format("Order %1s in chain is already migrated", orderWithTestRequestItem));
        }

        TestRequest testRequest=new TestRequest();
        testRequest.setPatient(order.getPatient());
        testRequest.setVisit(order.getEncounter().getVisit());
        testRequest.setEncounter(order.getEncounter());
        testRequest.setProvider(order.getOrderer());
        testRequest.setRequestDate(order.getDateActivated());
        testRequest.setRequestNo("LRN-" + order.getOrderNumber());
        testRequest.setUrgency(order.getUrgency());

        String clinicalNoteConceptUUid =  GlobalProperties.getClinicalNotesConceptUuid();
        if(StringUtils.isNotBlank(clinicalNoteConceptUUid)){
            Obs obs = order.getEncounter().getAllObs().stream().filter(p -> p.getConcept().getUuid().equalsIgnoreCase(clinicalNoteConceptUUid))
                    .findFirst().orElse(null);
            if(obs != null){
                testRequest.setClinicalNote(obs.getValueText());
            }
        }
        testRequest.setCareSetting(order.getCareSetting());

        // status and date stopped
        testRequest.setDateStopped(null);
        if(Order.FulfillerStatus.DECLINED.equals(order.getFulfillerStatus()) || Order.FulfillerStatus.EXCEPTION.equals(order.getFulfillerStatus())){
            testRequest.setStatus(TestRequestStatus.CANCELLED);
        }else if(Order.FulfillerStatus.COMPLETED.equals(order.getFulfillerStatus())){
            testRequest.setStatus(TestRequestStatus.COMPLETED);
        }else {
            testRequest.setStatus(TestRequestStatus.IN_PROGRESS);
        }

        testRequest.setAtLocation(order.getEncounter().getLocation());
        testRequest.setReferredIn(false);
        testRequest.setReferralFromFacility(null);
        testRequest.setReferralFromFacilityName(null);
        testRequest.setReferralInExternalRef(null);
        testRequest.setCreator(order.getCreator());
        testRequest.setDateCreated(order.getDateCreated());

        TestRequestItem testRequestItem = new TestRequestItem();
        testRequestItem.setTestRequest(testRequest);
        testRequestItem.setOrder(order);
        testRequestItem.setAtLocation(testRequest.getAtLocation());

        Location toLocation = null;
        LocationService locationService = Context.getLocationService();
        LocationTag mainLocationTag = locationService.getLocationTagByName(LabLocationTags.MAIN_LABORATORY_LOCATION_TAG);
        LocationTag locationTag = locationService.getLocationTagByName(LabLocationTags.LABORATORY_LOCATION_TAG);
        if(mainLocationTag != null || locationTag != null){
            List<Location> laboratories = Context.getLocationService()
                    .getLocationsHavingAnyTag(Stream.of(mainLocationTag, locationTag).filter(Objects::nonNull).collect(Collectors.toList()));
            if(!laboratories.isEmpty()){
                toLocation = laboratories.stream().min(Comparator.comparing(Location::getId)).orElse(null);
            }
        }
        testRequestItem.setToLocation(toLocation == null ? testRequestItem.getAtLocation() : toLocation);
        testRequestItem.setReferredOut(order.getInstructions() != null && order.getInstructions().startsWith("REFER"));
        if(testRequestItem.getReferredOut()){
            testRequestItem.setReferralOutOrigin(ReferralOutOrigin.Laboratory);
            testRequestItem.setReferralToFacilityName(order.getInstructions());
        }
        testRequestItem.setRequireRequestApproval(false);

        Obs orderObs = order.getEncounter().getObs().stream().filter(p->p.getOrder() != null &&
                p.getOrder().getOrderId().equals(order.getOrderId())).max(Comparator.comparing(Obs::getId)).orElse(null);
        testRequestItem.setCompleted(false);
        if(testRequestItem.getReferredOut()){
            if(orderObs == null){
                testRequestItem.setStatus(TestRequestItemStatus.REFERRED_OUT_LAB);
            }else{
                testRequest.setStatus(TestRequestStatus.COMPLETED);
                testRequestItem.setStatus(TestRequestItemStatus.COMPLETED);
                testRequestItem.setCompleted(true);
            }
        } else if(testRequest.getStatus() == TestRequestStatus.COMPLETED){
            testRequestItem.setStatus(TestRequestItemStatus.COMPLETED);
            testRequestItem.setCompleted(true);
        } else if(testRequest.getStatus() == TestRequestStatus.CANCELLED){
            testRequestItem.setStatus(TestRequestItemStatus.CANCELLED);
        }
        else if(orderObs != null){
            testRequestItem.setStatus(TestRequestItemStatus.IN_PROGRESS);
        } else if(StringUtils.isBlank(order.getAccessionNumber())){
            testRequestItem.setStatus(TestRequestItemStatus.SAMPLE_COLLECTION);
        }else {
            testRequestItem.setStatus(TestRequestItemStatus.IN_PROGRESS);
        }

        testRequestItem.setEncounter(order.getEncounter());
        testRequestItem.setReferralOutSample(null);
        testRequestItem.setReturnCount(0);
        testRequestItem.setDateCreated(testRequest.getDateCreated());
        testRequestItem.setCreator(testRequest.getCreator());

        String unknownConceptUuid = GlobalProperties.getUnknownConceptUuid();
        Concept unknownConcept = null;
        if(StringUtils.isNotBlank(unknownConceptUuid)){
            unknownConcept = Context.getConceptService().getConceptByUuid(unknownConceptUuid);
        }

        TestOrder testOrder = null;
        if(order instanceof TestOrder){
            testOrder = (TestOrder) order;
        }
        Sample sample = null;
        if(StringUtils.isNotBlank(order.getAccessionNumber()) || orderObs != null){
            sample=new Sample();
            sample.setParentSample(null);
            if(testOrder != null) {
                sample.setSampleType(testOrder.getSpecimenSource());
            }
            if(sample.getSampleType() == null && unknownConcept != null){
                sample.setSampleType(unknownConcept);
            }
            if(sample.getSampleType() == null){
                sample.setSampleType(order.getConcept());
            }
            sample.setAtLocation(testRequest.getAtLocation());
            sample.setAccessionNumber(order.getAccessionNumber() == null ? "UNKNOW" : order.getAccessionNumber());
            sample.setReferredOut(testRequestItem.getReferredOut());

            sample.setStatus( testRequestItem.getCompleted() ? SampleStatus.DISPOSED : SampleStatus.TESTING);
            sample.setEncounter(testRequest.getEncounter());
            sample.setReferralOutOrigin(testRequestItem.getReferralOutOrigin());
            sample.setReferralToFacilityName(testRequestItem.getReferralToFacilityName());
            sample.setDateCreated(order.getDateCreated());
            sample.setCreator(order.getCreator());
        }

        TestResult testResult = null;
        TestApproval testApproval = null;
        if(orderObs != null){
            testResult = new TestResult();
            testResult.setWorksheetItem(null);
            testResult.setOrder(order);
            testResult.setObs(orderObs);
            testResult.setResultBy(orderObs.getCreator());
            testResult.setStatus("Approval Not Required");
            testResult.setAtLocation(testRequest.getAtLocation());
            testResult.setResultDate(order.getDateCreated());
            testResult.setCompleted(TestRequestItemStatus.isCompletedProcess(testRequestItem.getStatus()));
            testResult.setCompletedResult(testResult.getCompleted() ? !TestRequestItemStatus.CANCELLED.equals(testRequestItem.getStatus()) :
                    false);
            testResult.setRequireApproval(!testResult.getCompleted());
            testResult.setCompletedDate(testResult.getCompleted() ? order.getDateActivated() : null);
            testResult.setDateCreated(orderObs.getDateCreated());
            testResult.setCreator(orderObs.getCreator());
        }

        if(testResult != null && !TestRequestItemStatus.isCompletedProcess(testRequestItem.getStatus())){
            TestConfig testConfig = dao.getTestConfigByConcept(order.getConcept().getConceptId());
            ApprovalFlow approvalFlow = null;
            Boolean completeItem = null;
            if(testConfig != null){
                if(testConfig.getRequireApproval()  == null || !testConfig.getRequireApproval()){
                    completeItem = true;
                }else{
                    approvalFlow = testConfig.getApprovalFlow();
                }
            }
            if(completeItem == null && approvalFlow == null){
                ApprovalFlowSearchFilter approvalFlowSearchFilter=new ApprovalFlowSearchFilter();
                approvalFlowSearchFilter.setNameOrSystemName("Default");
                approvalFlowSearchFilter.setVoided(false);
                Result<ApprovalFlowDTO> approvalFlowResult = dao.findApprovalFlows(approvalFlowSearchFilter);
                if(!approvalFlowResult.getData().isEmpty()){
                    approvalFlow = dao.getApprovalFlowById(approvalFlowResult.getData().get(0).getId());
                }else{
                    approvalFlowSearchFilter.setNameOrSystemName(null);
                    approvalFlowResult = dao.findApprovalFlows(approvalFlowSearchFilter);
                    if(!approvalFlowResult.getData().isEmpty()){
                        approvalFlow = dao.getApprovalFlowById(approvalFlowResult.getData().get(0).getId());
                    }
                }
            }

            if(completeItem == null && approvalFlow == null){
                completeItem = true;
            }

            if(Boolean.TRUE.equals(completeItem)){
                testRequestItem.setStatus(TestRequestItemStatus.COMPLETED);
                testRequestItem.setCompleted(true);
                testRequest.setStatus(TestRequestStatus.COMPLETED);
                testResult.setStatus("Approved");
                testResult.setCompleted(true);
                testResult.setRequireApproval(false);
                testResult.setCompletedDate(order.getDateActivated());
            }else{
                testResult.setRequireApproval(true);
                approvalFlow.setNextTestApproval(testResult,null,0);
            }
        }else if(testResult != null){
            testRequestItem.setStatus(TestRequestItemStatus.COMPLETED);
            testRequestItem.setCompleted(true);
            testRequest.setStatus(TestRequestStatus.COMPLETED);
            testResult.setCompleted(true);
            testResult.setStatus("Approved");
            testResult.setCompletedDate(order.getDateActivated());
            testResult.setCompletedResult(testRequestItem.getStatus().equals(TestRequestItemStatus.COMPLETED));
            testResult.setRequireApproval(false);
        }

        if(testRequestItem.getCompleted() != null && testRequestItem.getCompleted() && testRequestItem.getCompletedDate() == null){
            testRequestItem.setCompletedDate(order.getDateActivated());
        }

        testRequest = dao.saveTestRequest(testRequest);
        testRequestItem.setTestRequest(testRequest);
        testRequestItem = dao.saveTestRequestItem(testRequestItem);
        TestRequestItemSample testRequestItemSample = null;
        if(sample != null){
            sample.setTestRequest(testRequest);
            sample = dao.saveSample(sample);
            testRequestItemSample = new TestRequestItemSample();
            testRequestItemSample.setSample(sample);
            testRequestItemSample.setTestRequestItem(testRequestItem);
            testRequestItemSample.setDateCreated(sample.getDateCreated());
            testRequestItemSample.setCreator(sample.getCreator());
            testRequestItemSample = dao.saveTestRequestItemSample(testRequestItemSample);
            testRequestItemSample.setDateCreated(sample.getDateCreated());
            testRequestItemSample.setCreator(sample.getCreator());
        }

        if(testResult != null){
            testResult.setTestRequestItemSample(testRequestItemSample);
            testResult = dao.saveTestResult(testResult);

            if(testResult.getCurrentApproval() != null){
                dao.saveTestApproval(testResult.getCurrentApproval());
            }
        }

        boolean saveTestRequest = false;
        if(sample != null) {
            testRequestItem.setInitialSample(sample);
            saveTestRequest=true;
        }
        if(testResult != null) {
            testRequestItem.setFinalResult(testResult);
            testRequestItem.setResultDate(testResult.getResultDate());
            saveTestRequest=true;
        }

        if(saveTestRequest){
            dao.saveTestRequestItem(testRequestItem);
        }

        return new Pair<>(true, null);
    }

    public Result<TestRequestReportItem> findTestRequestReportItems(TestRequestReportItemFilter filter){
        Result<TestRequestReportItem> result = dao.findTestRequestReportItems(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getCollectedBy(),
                p.getResultBy(),
                p.getCurrentApprovalBy(),
                p.getRequestApprovalBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        for (TestRequestReportItem testRequestDTO : result.getData()) {
            if (testRequestDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCreatorMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getRequestApprovalBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getRequestApprovalBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setRequestApprovalFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setRequestApprovalMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setRequestApprovalGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getCollectedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCollectedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCollectedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCollectedByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setCollectedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getResultBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getResultBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setResultByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setResultByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setResultByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getCurrentApprovalBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCurrentApprovalBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCurrentApprovalByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCurrentApprovalByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setCurrentApprovalByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }
        return result;
    }

    public Map<Integer, List<ObsDto>> getObservations(List<Integer> orderIds){
        return dao.getObservations(orderIds);
    }

    public List<SummarizedTestReportItem> getSummarizedTestReport(Date startDate, Date endDate, Integer diagnosticLocation){
        return dao.getSummarizedTestReport(startDate, endDate, diagnosticLocation);
    }

    public List<SummarizedTestReportItem> getIndividualPerformanceReport(Date startDate, Date endDate, Integer diagnosticLocation, Integer testerUserId){
        return dao.getIndividualPerformanceReport(startDate, endDate, diagnosticLocation, testerUserId);
    }

    public Result<TestRequestReportItem> findTurnAroundTestRequestReportItems(TestRequestReportItemFilter filter){
        return dao.findTurnAroundTestRequestReportItems(filter);
    }

    public Result<TestRequestReportItem> findAuditReportReportItems(TestRequestReportItemFilter filter){
        Result<TestRequestReportItem> result = dao.findAuditReportReportItems(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getCollectedBy(),
                p.getResultBy(),
                p.getCurrentApprovalBy(),
                p.getRequestApprovalBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        for (TestRequestReportItem testRequestDTO : result.getData()) {
            if (testRequestDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCreatorMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getRequestApprovalBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getRequestApprovalBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setRequestApprovalFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setRequestApprovalMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setRequestApprovalGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getCollectedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCollectedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCollectedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCollectedByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setCollectedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getResultBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getResultBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setResultByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setResultByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setResultByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getCurrentApprovalBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCurrentApprovalBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCurrentApprovalByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCurrentApprovalByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setCurrentApprovalByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }
        return result;
    }


    public Result<TestRequestReportItem> findSampleCustodyReportItems(TestRequestReportItemFilter filter){
        Result<TestRequestReportItem> result = dao.findSampleCustodyReportItems(filter);
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getCollectedBy(),
                p.getResultBy(),
                p.getCurrentApprovalBy(),
                p.getRequestApprovalBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));

        for (TestRequestReportItem testRequestDTO : result.getData()) {
            if (testRequestDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCreatorMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getRequestApprovalBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getRequestApprovalBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setRequestApprovalFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setRequestApprovalMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setRequestApprovalGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getCollectedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCollectedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCollectedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCollectedByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setCollectedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getResultBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getResultBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setResultByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setResultByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setResultByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (testRequestDTO.getCurrentApprovalBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(testRequestDTO.getCurrentApprovalBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    testRequestDTO.setCurrentApprovalByFamilyName(userPersonNameDTO.get().getFamilyName());
                    testRequestDTO.setCurrentApprovalByMiddleName(userPersonNameDTO.get().getMiddleName());
                    testRequestDTO.setCurrentApprovalByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }
        return result;
    }

    public List<SampleActivityDTO> getSampleActivitiesForReport(List<Integer> sampleIds){
        return dao.getSampleActivitiesForReport(sampleIds);
    }

    public TestResultImportConfig getTestResultImportConfigByHeaderHash(String headerHash, Concept test){
        return dao.getTestResultImportConfigByHeaderHash(headerHash, test);
    }

    public TestConfig getTestConfigByConcept(Integer conceptId){
        return dao.getTestConfigByConcept(conceptId);
    }


    public TestResultImportConfig saveTestResultImportConfig(TestResultImportConfig testResultImportConfig){
        TestResultImportConfig result = dao.saveTestResultImportConfig(testResultImportConfig);
        return result;
    }

    public Result<StorageUnitDTO> findStorageUnits(StorageSearchFilter filter){
        Result<StorageUnitDTO> result = dao.findStorageUnits(filter);

        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        for (StorageUnitDTO storageUnitDTO : result.getData()) {
            if (storageUnitDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(storageUnitDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    storageUnitDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    storageUnitDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (storageUnitDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(storageUnitDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    storageUnitDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    storageUnitDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
        }

        return result;
    }

    public Result<StorageDTO> findStorages(StorageSearchFilter filter){
        Result<StorageDTO> result = dao.findStorages(filter);

        Map<Integer, List<StorageUnitDTO>> storageUnits = null;
        if(!result.getData().isEmpty() && filter.getIncludeUnits() != null && filter.getIncludeUnits()){
            StorageSearchFilter storageSearchFilter=new StorageSearchFilter();
            storageSearchFilter.setStorageIds(result.getData().stream().map(p->p.getId()).distinct().collect(Collectors.toList()));
            storageUnits = dao.findStorageUnits(storageSearchFilter).getData().stream().collect(Collectors.groupingBy(StorageUnitDTO::getStorageId));
        }
        List<UserPersonNameDTO> personNames = dao.getPersonNameByUserIds(result.getData().stream().map(p -> Arrays.asList(
                p.getCreator(),
                p.getChangedBy()
        )).flatMap(Collection::stream).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        for (StorageDTO storageDTO : result.getData()) {
            if (storageDTO.getCreator() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(storageDTO.getCreator())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    storageDTO.setCreatorFamilyName(userPersonNameDTO.get().getFamilyName());
                    storageDTO.setCreatorGivenName(userPersonNameDTO.get().getGivenName());
                }
            }
            if (storageDTO.getChangedBy() != null) {
                Optional<UserPersonNameDTO> userPersonNameDTO = personNames.stream().filter(p -> p.getUserId().equals(storageDTO.getChangedBy())).findFirst();
                if (userPersonNameDTO.isPresent()) {
                    storageDTO.setChangedByFamilyName(userPersonNameDTO.get().getFamilyName());
                    storageDTO.setChangedByGivenName(userPersonNameDTO.get().getGivenName());
                }
            }

            if(storageUnits != null){
                storageDTO.setUnits(storageUnits.getOrDefault(storageDTO.getId(), null));
            }
        }
        return result;
    }

    public Storage getStorageById(Integer id) {
        return dao.getStorageById(id);
    }

    public Storage getStorageByUuid(String uuid) {
        return dao.getStorageByUuid(uuid);
    }

    public Storage saveStorage(Storage storage){
        return dao.saveStorage(storage);
    }

    public void  deleteStorage(String storageUuid){
        Storage storage = dao.getStorageByUuid(storageUuid);
        if(storage == null) return;
        if(dao.checkStorageUsage(storage.getId())){
            invalidRequest("labmanagement.thinginuse", "Storage", "Samples");
        }
        /*List<StorageUnit> storageUnitsToDelete =  dao.getStorageUnitsByStorage(storage);
        for(StorageUnit storageUnit : storageUnitsToDelete){
            dao.deleteStorageUnitById(storageUnit.getId());
        }*/
        dao.deleteStorageUnitsByStorageId(storage.getId());
        dao.deleteStorageById(storage.getId());
    }

    public Storage saveStorage(StorageDTO storageDTO){
        Storage storage;
        boolean isNew;
        if (storageDTO.getUuid() == null) {
            isNew = true;
            storage = new Storage();
            storage.setCreator(Context.getAuthenticatedUser());
            storage.setDateCreated(new Date());
        } else {
            isNew = false;
            storage =  getStorageByUuid(storageDTO.getUuid());
            if (storage == null) {
                invalidRequest("labmanagement.thingnotexists", "Storage");
            }

            storage.setChangedBy(Context.getAuthenticatedUser());
            storage.setDateChanged(new Date());
        }

        Location location = null;
        if(StringUtils.isBlank(storageDTO.getAtLocationUuid())){
            invalidRequest("labmanagement.fieldrequired", "Lab Section");
        }else{
            location = Context.getLocationService().getLocationByUuid(storageDTO.getAtLocationUuid());
            if(location == null){
                invalidRequest("labmanagement.thingnotexists",  "Lab Section");
            }
        }

        if(StringUtils.isBlank(storageDTO.getName())){
            invalidRequest("labmanagement.fieldrequired", "Name");
        }else{
            StorageSearchFilter searchFilter = new StorageSearchFilter();
            searchFilter.setStorageName(storageDTO.getName());
            searchFilter.setLocationId(location.getLocationId());
            if(findStorages(searchFilter).getData().stream().anyMatch(p-> isNew || !p.getId().equals(storage.getId()))){
                invalidRequest("labmanagement.storage.nameexists");
            }
        }

        boolean capacityHasChanged = false;
        Integer capacity = storage.getCapacity();
        Integer oldCapacity = storage.getCapacity();
        if(isNew){
            if(storageDTO.getCapacity() == null){
                invalidRequest("labmanagement.fieldrequired", "Capacity");
            }
            if(storageDTO.getCapacity() <= 0){
                invalidRequest("labmanagement.fieldinvalid", "Capacity");
            }
            capacity = storageDTO.getCapacity();
        }
        else{
            if(storageDTO.getCapacity() != null && !storageDTO.getCapacity().equals(capacity)){
                capacityHasChanged = true;
                if(dao.checkStorageUsage(storage.getId())){
                    invalidRequest("labmanagement.storage.inusecapacitynochange", "Capacity");
                }
                capacity = storageDTO.getCapacity();
            }
        }

        storage.setName(storageDTO.getName());
        storage.setDescription(storageDTO.getDescription());
        storage.setActive(storageDTO.getActive() == null || storageDTO.getActive());
        storage.setAtLocation(location);
        storage.setCapacity(capacity);
        storage.setCreator(Context.getAuthenticatedUser());
        storage.setDateCreated(new Date());
        dao.saveStorage(storage);

        List<StorageUnit> storageUnits = null;
        if(isNew || capacityHasChanged){
            if(isNew){
                storageUnits = new ArrayList<>();
                Integer unitsToCreate = storage.getCapacity();
                for(int index = 1; index <= unitsToCreate; index++){
                    StorageUnit storageUnit=new StorageUnit();
                    storageUnit.setStorage(storage);
                    storageUnit.setActive(true);
                    storageUnit.setUnitName(Integer.toString(index));
                    storageUnit.setDescription(null);
                    storageUnit.setCreator(Context.getAuthenticatedUser());
                    storageUnit.setDateCreated(new Date());
                    storageUnits.add(storageUnit);
                }
            }else if(oldCapacity != null){
                if(oldCapacity < storage.getCapacity()){
                    storageUnits = new ArrayList<>();
                    for(int index = oldCapacity + 1; index <= storage.getCapacity(); index++){
                        StorageUnit storageUnit=new StorageUnit();
                        storageUnit.setStorage(storage);
                        storageUnit.setActive(true);
                        storageUnit.setUnitName(Integer.toString(index));
                        storageUnit.setDescription(null);
                        storageUnit.setCreator(Context.getAuthenticatedUser());
                        storageUnit.setDateCreated(new Date());
                        storageUnits.add(storageUnit);

                    }
                }else{
                    List<StorageUnit> storageUnitsToDelete =  dao.getStorageUnitsByStorage(storage).stream().sorted(Comparator.comparing(StorageUnit::getId)).skip(storage.getCapacity()).collect(Collectors.toList());
                    for(StorageUnit storageUnit : storageUnitsToDelete){
                        dao.deleteStorageUnitById(storageUnit.getId());
                    }
                    dao.saveStorage(storage);
                }
            }

        }

        if(storageUnits != null){
            for(StorageUnit storageUnit : storageUnits){
                storage.addStorageUnit(storageUnit);
                dao.saveStorageUnit(storageUnit);
            }
            dao.saveStorage(storage);
        }

        return storage;
    }

    public StorageUnit getStorageUnitById(Integer id) {
        return dao.getStorageUnitById(id);
    }

    public StorageUnit getStorageUnitByUuid(String uuid){
        return dao.getStorageUnitByUuid(uuid);
    }


    public Sample getSampleByUuid(String sampleUuid){
        return dao.getSampleByUuid(sampleUuid);
    }

    public ApprovalDTO disposeSamples(TestRequestAction testRequestAction){
        if(testRequestAction == null){
            invalidRequest("labmanagement.fieldrequired", "Action information");
        }

        if(testRequestAction.getRecords() == null || testRequestAction.getRecords().isEmpty()){
            invalidRequest("labmanagement.fieldrequired", "Records");
        }

        if(testRequestAction.getRecords().size() > 100){
            invalidRequest("labmanagement.fieldrequired", "Records not exceeding 100");
        }

        if(testRequestAction.getAction() == null){
            invalidRequest("labmanagement.fieldrequired", "Action");
        }

        if(testRequestAction.getActionDate() == null){
            invalidRequest("labmanagement.fieldrequired", "Disposal date");
        }

        if(testRequestAction.getActionDate().after(new Date())){
            invalidRequest("labmanagement.futuredatenotallowed", "Disposal date");
        }

        if(!StringUtils.isBlank(testRequestAction.getRemarks()) && testRequestAction.getRemarks().length() > 500){
            invalidRequest("labmanagement.thingnotexceeds", "Name", "500");
        }
        if(testRequestAction.getParameters() == null){
            testRequestAction.setParameters(new LinkedHashMap<>());
        }
        Object thawCyles = testRequestAction.getParameters().getOrDefault("thawCycles", null);
        if(thawCyles != null){
            thawCyles = Integer.parseInt(thawCyles.toString());
            if((Integer) thawCyles < 0){
                invalidRequest("labmanagement.fieldrequired", "Thaw Cycles greater than zero");
            }
        }

        Concept volumeUnit = null;
        String volumeUnitUuid = (String)testRequestAction.getParameters().getOrDefault("volumeUnitUuid", null);
        Object volume = testRequestAction.getParameters().getOrDefault("volume", null);
        boolean requireVolumeInfo = StringUtils.isNotBlank(volumeUnitUuid) || volume != null;
        if(requireVolumeInfo){
            if(StringUtils.isBlank(volumeUnitUuid)){
                invalidRequest("labmanagement.fieldrequired", "Volume unit");
            }
            volumeUnit = Context.getConceptService().getConceptByUuid(volumeUnitUuid);
            if (volumeUnit == null) {
                invalidRequest("labmanagement.thingnotexists", "Volume unit");
            }

            if(volume == null){
                invalidRequest("labmanagement.fieldrequired", "Volume");
            }
            volume = new BigDecimal(volume.toString());
            if(((BigDecimal)volume).compareTo(BigDecimal.ZERO) < 0){
                invalidRequest("labmanagement.fieldrequired", "Volume greater than or equal to zero");
            }
        }

        List<String> records = testRequestAction.getRecords().stream().distinct().collect(Collectors.toList());
        List<Sample> samples = dao.getSamplesByUuid(testRequestAction.getRecords(), false);
        if(samples.size() != records.size()){
            invalidRequest("labmanagement.notexists",
                    String.format("%1s Record(s)", records.size() - samples.size()));
        }

        boolean verifiedRepositoryDisposal = false;
        User responsiblePerson = null;
        String responsiblePersonOther = null;
        for(Sample sample : samples){
            if(sample.getStatus() == null ||
                    !SampleStatus.canDispose(sample)){
                invalidRequest("labmanagement.actionnotallowed", "Disposal", sample.getAccessionNumber());
            }
            if(sample.getStorageUnit() != null){
                verifiedRepositoryDisposal = true;

                if(!Context.getAuthenticatedUser().hasPrivilege(Privileges.TASK_LABMANAGEMENT_REPOSITORY_MUTATE)){
                    invalidRequest("labmanagement.notauthorised");
                }

                if(StringUtils.isBlank(testRequestAction.getRemarks())){
                    invalidRequest("labmanagement.fieldrequired", "Remarks");
                }

                if(testRequestAction.getParameters().containsKey("responsiblePersonUuid")){
                    String responsiblePersonUuid =(String) testRequestAction.getParameters().get("responsiblePersonUuid");
                    if(!StringUtils.isBlank(responsiblePersonUuid)) {
                        responsiblePerson = Context.getUserService().getUserByUuid(responsiblePersonUuid);
                        if (responsiblePerson == null) {
                            invalidRequest("labmanagement.thingnotexists", "Responsible Person");
                        }
                    }
                }

                if(responsiblePerson == null && testRequestAction.getParameters().containsKey("responsiblePersonOther")){
                    responsiblePersonOther= (String) testRequestAction.getParameters().get("responsiblePersonOther");
                }


                if(responsiblePerson == null && StringUtils.isBlank(responsiblePersonOther)){
                    invalidRequest("labmanagement.fieldrequired", "Responsible Person");
                }

                if(testRequestAction.getActionDate() == null){
                    invalidRequest("labmanagement.fieldrequired", "Disposal date");
                }

                if(testRequestAction.getActionDate().after(new Date())){
                    invalidRequest("labmanagement.futuredatenotallowed", "Disposal date");
                }

            }
        }

        String remarks = StringUtils.isBlank(testRequestAction.getRemarks()) ? null : testRequestAction.getRemarks().trim();
        User currentUser = Context.getAuthenticatedUser();
        for(Sample sample1 : samples){

            SampleActivity sampleActivity = getSampleActivity(sample1, currentUser, remarks);
            sampleActivity.setActivityType(SampleActivityType.DISPOSAL);
            sampleActivity.setActivityDate(testRequestAction.getActionDate());
            sampleActivity.setDestinationState(SampleStatus.DISPOSED.name());
            sampleActivity.setStatus(SampleStatus.DISPOSED.name());
            sampleActivity.setResponsiblePerson(verifiedRepositoryDisposal ?  responsiblePerson : Context.getAuthenticatedUser());
            sampleActivity.setResponsiblePersonOther(responsiblePersonOther);
            if(volume != null) sampleActivity.setVolume((BigDecimal) volume);
            sampleActivity.setVolumeUnit(volumeUnit);
            if(thawCyles != null) sampleActivity.setThawCycles((Integer) thawCyles);
            sampleActivity.setActivityDate(testRequestAction.getActionDate() == null ? new Date() : testRequestAction.getActionDate());
            sampleActivity = dao.saveSampleActivity(sampleActivity);

            sample1.setStatus(SampleStatus.DISPOSED);
            if(verifiedRepositoryDisposal || sample1.getStorageStatus() != null){
                sample1.setStorageStatus(StorageStatus.DISPOSED);
            }
            sample1.setDateChanged(new Date());
            sample1.setChangedBy(currentUser);
            sample1.setStorageUnit(null);
            if(volume != null){
                sample1.setVolume((BigDecimal) volume);
                sample1.setVolumeUnit(volumeUnit);
            }
            sample1.setCurrentSampleActivity(sampleActivity);
            dao.saveSample(sample1);

        }
        ApprovalDTO approvalDTO=new ApprovalDTO();
        approvalDTO.setRemarks(remarks);
        approvalDTO.setResult(testRequestAction.getAction());
        return approvalDTO;
    }

    private static SampleActivity getSampleActivity(Sample sample1, User currentUser, String remarks) {
        SampleActivity sampleActivity=new SampleActivity();
        sampleActivity.setSample(sample1);
        sampleActivity.setActivityBy(currentUser);
        sampleActivity.setToSample(null);
        sampleActivity.setSource(sample1.getAtLocation());
        sampleActivity.setSourceState( sample1.getStatus().name());
        sampleActivity.setDestination(null);
        sampleActivity.setRemarks(remarks);
        sampleActivity.setToSample(null);
        sampleActivity.setReusedCheckout(null);
        sampleActivity.setVolume(sample1.getVolume());
        sampleActivity.setVolumeUnit(sample1.getVolumeUnit());
        sampleActivity.setThawCycles(null);
        sampleActivity.setCreator(currentUser);
        sampleActivity.setDateCreated(new Date());
        sampleActivity.setStorageUnit(sample1.getStorageUnit());
        return sampleActivity;
    }

    public ApprovalDTO archiveSamples(TestRequestAction testRequestAction){
        if(testRequestAction == null){
            invalidRequest("labmanagement.fieldrequired", "Action information");
        }

        if(testRequestAction.getRecords() == null || testRequestAction.getRecords().isEmpty()){
            invalidRequest("labmanagement.fieldrequired", "Records");
        }

        if(testRequestAction.getRecords().size() > 1){
            invalidRequest("labmanagement.fieldrequired", "Records not exceeding one");
        }

        /*if(testRequestAction.getAction() == null){
            invalidRequest("labmanagement.fieldrequired", "Action");
        }

        if(StringUtils.isBlank(testRequestAction.getRemarks())){
            invalidRequest("labmanagement.fieldrequired", "Remarks");
        }*/

        if(!StringUtils.isBlank(testRequestAction.getRemarks()) && testRequestAction.getRemarks().length() > 500){
            invalidRequest("labmanagement.thingnotexceeds", "Name", "500");
        }

        if(testRequestAction.getParameters() == null || testRequestAction.getParameters().isEmpty() ||
        !testRequestAction.getParameters().containsKey("storageUnitUuid")){
            invalidRequest("labmanagement.fieldrequired", "Storage unit");
        }

        String storageUnitUuid =(String) testRequestAction.getParameters().get("storageUnitUuid");
        if(StringUtils.isBlank(storageUnitUuid)){
            invalidRequest("labmanagement.fieldrequired", "Storage unit");
        }
        StorageUnit storageUnit = getStorageUnitByUuid(storageUnitUuid);
        if(storageUnit == null){
            invalidRequest("labmanagement.thingnotexists", "Storage unit");
        }

        String responsiblePersonOther = null;
        User responsiblePerson = null;
        if(testRequestAction.getParameters().containsKey("responsiblePersonUuid")){
            String responsiblePersonUuid =(String) testRequestAction.getParameters().get("responsiblePersonUuid");
            if(!StringUtils.isBlank(responsiblePersonUuid)) {
                responsiblePerson = Context.getUserService().getUserByUuid(responsiblePersonUuid);
                if (responsiblePerson == null) {
                    invalidRequest("labmanagement.thingnotexists", "Responsible Person");
                }
            }
        }

        if(responsiblePerson == null && testRequestAction.getParameters().containsKey("responsiblePersonOther")){
            responsiblePersonOther= (String) testRequestAction.getParameters().get("responsiblePersonOther");
        }

        if(responsiblePerson == null && StringUtils.isBlank(responsiblePersonOther)){
            invalidRequest("labmanagement.fieldrequired", "Responsible Person");
        }

        if(testRequestAction.getActionDate() == null){
            invalidRequest("labmanagement.fieldrequired", "Archival date");
        }

        if(testRequestAction.getActionDate().after(new Date())){
            invalidRequest("labmanagement.futuredatenotallowed", "Archival date");
        }

        if(dao.checkStorageUnitAssigned(storageUnit)){
            invalidRequest("labmanagement.storagealreadyassigned", "Storage unit");
        }

        Object thawCyles = testRequestAction.getParameters().getOrDefault("thawCycles", null);
        if(thawCyles != null){
            thawCyles = Integer.parseInt(thawCyles.toString());
            if((Integer) thawCyles < 0){
                invalidRequest("labmanagement.fieldrequired", "Thaw Cycles greater than zero");
            }
        }

        Concept volumeUnit = null;
        String volumeUnitUuid = (String)testRequestAction.getParameters().getOrDefault("volumeUnitUuid", null);
        Object volume = testRequestAction.getParameters().getOrDefault("volume", null);
        boolean requireVolumeInfo = StringUtils.isNotBlank(volumeUnitUuid) || volume != null;
        if(requireVolumeInfo){
            if(StringUtils.isBlank(volumeUnitUuid)){
                invalidRequest("labmanagement.fieldrequired", "Volume unit");
            }
            volumeUnit = Context.getConceptService().getConceptByUuid(volumeUnitUuid);
            if (volumeUnit == null) {
                invalidRequest("labmanagement.thingnotexists", "Volume unit");
            }

            if(volume == null){
                invalidRequest("labmanagement.fieldrequired", "Volume");
            }
            volume = new BigDecimal(volume.toString());
            if(((BigDecimal)volume).compareTo(BigDecimal.ZERO) < 0){
                invalidRequest("labmanagement.fieldrequired", "Volume greater than or equal to zero");
            }
        }

        List<String> records = testRequestAction.getRecords().stream().distinct().collect(Collectors.toList());
        List<Sample> samples = dao.getSamplesByUuid(testRequestAction.getRecords(), false);
        if(samples.size() != records.size()){
            invalidRequest("labmanagement.notexists",
                    String.format("%1s Record(s)", records.size() - samples.size()));
        }

        for(Sample sample : samples){
            if(sample.getStatus() == null ||
                    !SampleStatus.canArchive(sample)){
                invalidRequest("labmanagement.actionnotallowed", "Archive", sample.getAccessionNumber());
            }
        }

        String remarks = StringUtils.isBlank(testRequestAction.getRemarks()) ? null : testRequestAction.getRemarks().trim();
        User currentUser = Context.getAuthenticatedUser();
        for(Sample sample1 : samples){

            SampleActivity sampleActivity = getSampleActivity(sample1, currentUser, remarks);
            archiveSample(sample1, testRequestAction.getActionDate(), sampleActivity, storageUnit, responsiblePerson, responsiblePersonOther, volume, volumeUnit, thawCyles, currentUser);

        }
        ApprovalDTO approvalDTO=new ApprovalDTO();
        approvalDTO.setRemarks(remarks);
        approvalDTO.setResult(testRequestAction.getAction());
        return approvalDTO;
    }

    protected void archiveSample(Sample sample1, Date actionDate, SampleActivity sampleActivity, StorageUnit storageUnit, User responsiblePerson, String responsiblePersonOther, Object volume, Concept volumeUnit, Object thawCyles, User currentUser) {
        sampleActivity.setActivityType(SampleActivityType.ARCHIVE);
        sampleActivity.setActivityDate(actionDate);
        sampleActivity.setDestinationState(SampleStatus.ARCHIVED.name());
        sampleActivity.setStatus(SampleStatus.ARCHIVED.name());
        sampleActivity.setStorageUnit(storageUnit);
        sampleActivity.setResponsiblePerson(responsiblePerson);
        sampleActivity.setResponsiblePersonOther(responsiblePersonOther);
        if(volume != null) sampleActivity.setVolume((BigDecimal) volume);
        sampleActivity.setVolumeUnit(volumeUnit);
        if(thawCyles != null) sampleActivity.setThawCycles((Integer) thawCyles);
        sampleActivity.setActivityDate(actionDate == null ? new Date() : actionDate);
        sampleActivity = dao.saveSampleActivity(sampleActivity);

        sample1.setStatus(SampleStatus.ARCHIVED);
        sample1.setStorageStatus(StorageStatus.ARCHIVED);
        sample1.setDateChanged(new Date());
        sample1.setChangedBy(currentUser);
        sample1.setStorageUnit(storageUnit);
        if(volume != null){
            sample1.setVolume((BigDecimal) volume);
            sample1.setVolumeUnit(volumeUnit);
        }
        sample1.setCurrentSampleActivity(sampleActivity);
        dao.saveSample(sample1);
    }

    public ApprovalDTO checkOutSamples(TestRequestAction testRequestAction){
        if(testRequestAction == null){
            invalidRequest("labmanagement.fieldrequired", "Action information");
        }

        if(testRequestAction.getRecords() == null || testRequestAction.getRecords().isEmpty()){
            invalidRequest("labmanagement.fieldrequired", "Records");
        }

        if(testRequestAction.getRecords().size() > 1){
            invalidRequest("labmanagement.fieldrequired", "Records not exceeding one");
        }

        /*
        if(testRequestAction.getAction() == null){
            invalidRequest("labmanagement.fieldrequired", "Action");
        }

        if(StringUtils.isBlank(testRequestAction.getRemarks())){
            invalidRequest("labmanagement.fieldrequired", "Remarks");
        }*/

        if(!StringUtils.isBlank(testRequestAction.getRemarks()) && testRequestAction.getRemarks().length() > 500){
            invalidRequest("labmanagement.thingnotexceeds", "Name", "500");
        }
        /*
        if(testRequestAction.getParameters() == null || testRequestAction.getParameters().isEmpty() ||
                !testRequestAction.getParameters().containsKey("storageUnitUuid")){
            invalidRequest("labmanagement.fieldrequired", "Storage unit");
        }*/

        String responsiblePersonOther = null;
        User responsiblePerson = null;
        if(testRequestAction.getParameters().containsKey("responsiblePersonUuid")){
            String responsiblePersonUuid =(String) testRequestAction.getParameters().get("responsiblePersonUuid");
            if(!StringUtils.isBlank(responsiblePersonUuid)) {
                responsiblePerson = Context.getUserService().getUserByUuid(responsiblePersonUuid);
                if (responsiblePerson == null) {
                    invalidRequest("labmanagement.thingnotexists", "Responsible Person");
                }
            }
        }

        if(responsiblePerson == null && testRequestAction.getParameters().containsKey("responsiblePersonOther")){
            responsiblePersonOther= (String) testRequestAction.getParameters().get("responsiblePersonOther");
        }

        if(responsiblePerson == null && StringUtils.isBlank(responsiblePersonOther)){
            invalidRequest("labmanagement.fieldrequired", "Responsible Person");
        }

        if(testRequestAction.getActionDate() == null){
            invalidRequest("labmanagement.fieldrequired", "Check-Out date");
        }

        if(testRequestAction.getActionDate().after(new Date())){
            invalidRequest("labmanagement.futuredatenotallowed", "Check-Out date");
        }

        Object thawCyles = testRequestAction.getParameters().getOrDefault("thawCycles", null);
        if(thawCyles != null){
            thawCyles = Integer.parseInt(thawCyles.toString());
            if((Integer) thawCyles < 0){
                invalidRequest("labmanagement.fieldrequired", "Thaw Cycles greater than zero");
            }
        }

        Concept volumeUnit = null;
        String volumeUnitUuid = (String)testRequestAction.getParameters().getOrDefault("volumeUnitUuid", null);
        Object volume = testRequestAction.getParameters().getOrDefault("volume", null);
        boolean requireVolumeInfo = StringUtils.isNotBlank(volumeUnitUuid) || volume != null;
        if(requireVolumeInfo){
            if(StringUtils.isBlank(volumeUnitUuid)){
                invalidRequest("labmanagement.fieldrequired", "Volume unit");
            }
            volumeUnit = Context.getConceptService().getConceptByUuid(volumeUnitUuid);
            if (volumeUnit == null) {
                invalidRequest("labmanagement.thingnotexists", "Volume unit");
            }

            if(volume == null){
                invalidRequest("labmanagement.fieldrequired", "Volume");
            }
            volume = new BigDecimal(volume.toString());
            if(((BigDecimal)volume).compareTo(BigDecimal.ZERO) < 0){
                invalidRequest("labmanagement.fieldrequired", "Volume greater than or equal to zero");
            }
        }

        List<String> records = testRequestAction.getRecords().stream().distinct().collect(Collectors.toList());
        List<Sample> samples = dao.getSamplesByUuid(testRequestAction.getRecords(), false);
        if(samples.size() != records.size()){
            invalidRequest("labmanagement.notexists",
                    String.format("%1s Record(s)", records.size() - samples.size()));
        }

        for(Sample sample : samples){
            if(sample.getStatus() == null ||
                    !SampleStatus.canCheckOut(sample)){
                invalidRequest("labmanagement.actionnotallowed", "Check-Out", sample.getAccessionNumber());
            }
        }

        String remarks = StringUtils.isBlank(testRequestAction.getRemarks()) ? null : testRequestAction.getRemarks().trim();
        User currentUser = Context.getAuthenticatedUser();
        for(Sample sample1 : samples){

            SampleStatus newSampleStatus = SampleStatus.TESTING;
            if(sample1.getCurrentSampleActivity() != null && StringUtils.isNotBlank(sample1.getCurrentSampleActivity().getSourceState())){
                newSampleStatus = SampleStatus.findByName(sample1.getCurrentSampleActivity().getSourceState());
            }

            SampleActivity sampleActivity = getSampleActivity(sample1, currentUser, remarks);
            sampleActivity.setActivityType(SampleActivityType.CHECKOUT);
            sampleActivity.setActivityDate(testRequestAction.getActionDate());
            sampleActivity.setDestinationState(SampleActivityType.CHECKOUT.name());
            sampleActivity.setStatus(SampleActivityType.CHECKOUT.name());
            sampleActivity.setResponsiblePerson( responsiblePerson);
            sampleActivity.setResponsiblePersonOther(responsiblePersonOther);
            if(volume != null) sampleActivity.setVolume((BigDecimal) volume);
            sampleActivity.setVolumeUnit(volumeUnit);
            if(thawCyles != null) sampleActivity.setThawCycles((Integer) thawCyles);
            sampleActivity.setActivityDate(testRequestAction.getActionDate() == null ? new Date() : testRequestAction.getActionDate());
            sampleActivity = dao.saveSampleActivity(sampleActivity);

            sample1.setStatus(newSampleStatus == null ? SampleStatus.TESTING : newSampleStatus);
            sample1.setStorageStatus(StorageStatus.CHECKED_OUT);
            sample1.setDateChanged(new Date());
            sample1.setChangedBy(currentUser);
            sample1.setStorageUnit(null);
            if(volume != null){
                sample1.setVolume((BigDecimal) volume);
                sample1.setVolumeUnit(volumeUnit);
            }
            sample1.setCurrentSampleActivity(sampleActivity);
            dao.saveSample(sample1);

        }
        ApprovalDTO approvalDTO=new ApprovalDTO();
        approvalDTO.setRemarks(remarks);
        approvalDTO.setResult(testRequestAction.getAction());
        return approvalDTO;
    }

    public Result<SampleActivityDTO> findSampleActivities(SampleActivitySearchFilter filter){
        return dao.findSampleActivities(filter);
    }

    public SampleActivity getSampleActivityById(Integer id){
        return dao.getSampleActivityById(id);
    }

    public Document getDocumentById(Integer id){
        return dao.getDocumentById(id);
    }
}
