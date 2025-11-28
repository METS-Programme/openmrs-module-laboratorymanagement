/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * <p>
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.labmanagement.api;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.labmanagement.api.dto.*;
import org.openmrs.module.labmanagement.api.model.*;
import org.openmrs.module.labmanagement.api.reporting.Report;
import org.openmrs.module.labmanagement.api.utils.Pair;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Collection;
import java.util.Map;

/**
 * The main service of this module, which is exposed for other modules. See
 * moduleApplicationContext.xml on how it is wired up.
 */
public interface LabManagementService extends OpenmrsService {
    @Transactional
    @Authorized(value = "MANAGE_LOCATIONS")
    void deleteLocation(String uuid);

    @Transactional(readOnly = true)
    //@Authorized(Privileges.APP_LABMANAGEMENT_TESTCONFIGURATIONS)
    Result<TestConfigDTO> findTestConfigurations(TestConfigSearchFilter filter);

    @Transactional(readOnly = true)
    //@Authorized(Privileges.APP_LABMANAGEMENT_TESTCONFIGURATIONS)
    TestConfig getTestConfigurationById(Integer id);

    @Transactional(readOnly = true)
    //@Authorized(Privileges.APP_LABMANAGEMENT_TESTCONFIGURATIONS)
    TestConfig getTestConfigurationByUuid(String uuid);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTCONFIGURATIONS_MUTATE)
    TestConfig saveTestConfig(TestConfigDTO testConfigDTO);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTCONFIGURATIONS_MUTATE)
    TestConfig saveTestConfig(TestConfig testConfig);

    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTCONFIGURATIONS_MUTATE)
    ImportResult importTestConfigurations(Path file, boolean hasHeader);

    @Transactional(readOnly = true)
    //@Authorized(Privileges.APP_LABMANAGEMENT_APPROVALCONFIGURATIONS)
    Result<ApprovalFlowDTO> findApprovalFlows(ApprovalFlowSearchFilter filter);

    @Transactional(readOnly = true)
    //@Authorized(Privileges.APP_LABMANAGEMENT_APPROVALCONFIGURATIONS)
    ApprovalFlow getApprovalFlowById(Integer id);

    @Transactional(readOnly = true)
    //@Authorized(Privileges.APP_LABMANAGEMENT_APPROVALCONFIGURATIONS)
    ApprovalFlow getApprovalFlowByUuid(String uuid);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_APPROVALCONFIGURATIONS_MUTATE)
    ApprovalFlow saveApprovalFlow(ApprovalFlowDTO approvalFlowDTO);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_APPROVALCONFIGURATIONS_MUTATE)
    void deleteApprovalFlow(String uuid);

    @Transactional(readOnly = true)
    List<Concept> getConcepts(Collection<Integer> conceptIds);

    @Transactional(readOnly = true)
    List<ApprovalFlow> getApprovalFlowsBySystemName(List<String> approvalFlowSystemNames);

    @Transactional(readOnly = true)
    List<TestConfig> getTestConfigsByIds(Collection<Integer> testConfigIds);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_APPROVALCONFIGURATIONS_MUTATE)
    ApprovalFlow saveApprovalFlow(ApprovalFlow approvalFlow);

    @Transactional(readOnly = true)
    //@Authorized(Privileges.APP_LABMANAGEMENT_APPROVALCONFIGURATIONS)
    Result<ApprovalConfigDTO> findApprovalConfigurations(ApprovalConfigSearchFilter filter);

    @Transactional(readOnly = true)
    //@Authorized(Privileges.APP_LABMANAGEMENT_APPROVALCONFIGURATIONS)
    ApprovalConfig getApprovalConfigById(Integer id);

    @Transactional(readOnly = true)
    //@Authorized(Privileges.APP_LABMANAGEMENT_APPROVALCONFIGURATIONS)
    ApprovalConfig getApprovalConfigByUuid(String uuid);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_APPROVALCONFIGURATIONS_MUTATE)
    ApprovalConfig saveApprovalConfig(ApprovalConfigDTO approvalConfigDTO);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_APPROVALCONFIGURATIONS_MUTATE)
    void deleteApprovalConfig(String uuid);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTREQUESTS_MUTATE)
    TestRequest saveTestRequest(TestRequestDTO testRequestDTO);

    @Transactional(readOnly = true)
    ReferralLocation getReferralLocationById(Integer id);

    @Transactional(readOnly = true)
    ReferralLocation getReferralLocationByUuid(String uuid);

    @Transactional(readOnly = true)
    Result<ReferralLocationDTO> findReferralLocations(ReferralLocationSearchFilter filter);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_APPROVALCONFIGURATIONS_MUTATE)
    ReferralLocation saveReferralLocation(ReferralLocationDTO referralLocationDTO);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTREQUESTS)
    Result<TestRequestDTO> findTestRequests(TestRequestSearchFilter filter);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTREQUESTS)
   List<TestRequestItem> getTestRequestItemByOrder(Order order);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTREQUESTS)
    TestRequest getTestRequestById(Integer id);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTREQUESTS)
    TestRequest getTestRequestByUuid(String uuid);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTREQUESTS_MUTATE)
    TestRequest saveTestRequest(TestRequest testRequest);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTREQUESTS)
    TestRequestItem getTestRequestItemById(Integer id);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTREQUESTS)
    TestRequestItem getTestRequestItemByUuid(String uuid);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTREQUESTS_MUTATE)
    TestRequestItem saveTestRequestItem(TestRequestItem testRequestItem);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTREQUESTS)
    Result<TestRequestItemDTO> findTestRequestItems(TestRequestItemSearchFilter filter);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTREQUESTS_APPROVE)
    ApprovalDTO approveTestRequestItem(TestRequestAction testRequestAction);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_SAMPLES)
    List<Integer> findSamples(String text, boolean includeAll, int maxItems);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_SAMPLES)
    Result<SampleDTO> findSamples(SampleSearchFilter filter);

    @Transactional
    @Authorized({ Privileges.TASK_LABMANAGEMENT_SAMPLES_COLLECT,Privileges.TASK_LABMANAGEMENT_SAMPLES_MUTATE})
    void deleteSampleByUuid(String sampleUuid);

    @Transactional
    @Authorized({ Privileges.TASK_LABMANAGEMENT_SAMPLES_COLLECT,Privileges.TASK_LABMANAGEMENT_SAMPLES_MUTATE})
    Pair<Sample, Map<Integer, String>> saveSample(SampleDTO sample);

    @Transactional
    @Authorized({ Privileges.TASK_LABMANAGEMENT_SAMPLES_COLLECT,Privileges.TASK_LABMANAGEMENT_SAMPLES_MUTATE})
    Pair<Sample, Map<Integer, String>> useExistingSampleForTest(TestRequestAction testRequestAction);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_SAMPLES_COLLECT)
    Map<Integer, String> releaseSamplesForTesting(String testRequestUuid, List<String> sampleUuids);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_SAMPLES_COLLECT)
    void updateOrderInstructions(Map<Integer, String> orderInstructions);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_WORKSHEETS)
    Worksheet getWorksheetById(Integer id);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_WORKSHEETS)
    Worksheet getWorksheetByUuid(String uuid);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_WORKSHEETS)
    Result<WorksheetDTO> findWorksheets(WorksheetSearchFilter filter);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_WORKSHEETS_MUTATE)
    Worksheet saveWorksheet(WorksheetDTO worksheet);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_WORKSHEETS_MUTATE)
    Worksheet saveWorksheet(Worksheet worksheet);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_WORKSHEETS)
    WorksheetItem getWorksheetItemById(Integer id);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_WORKSHEETS)
    WorksheetItem getWorksheetItemByUuid(String uuid);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_WORKSHEETS_MUTATE)
    WorksheetItem saveWorksheetItem(WorksheetItem worksheetItem);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_WORKSHEETS_MUTATE)
    void deleteWorksheetItem(Integer workSheetItemId, String reason);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_WORKSHEETS_MUTATE)
    void deleteWorksheet(Integer workSheetId, String reason);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_WORKSHEETS)
    Result<WorksheetItemDTO> findWorksheetItems(WorksheetItemSearchFilter filter);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTRESULTS)
    Result<TestResultDTO> findTestResults(TestResultSearchFilter filter);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTRESULTS_MUTATE)
    TestResult saveTestResult(TestResultDTO testResultDTO);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTRESULTS_MUTATE)
    TestResult saveTestResult(TestResultDTO testResultDTO, Worksheet worksheetToValidateAgainst);

    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTRESULTS_MUTATE)
    List<TestResult> saveWorksheetTestResults(WorksheetTestResultDTO worksheetTestResultDTO);

    @Transactional(readOnly = true)
    List<Integer> getWorksheetTestResultIdsForAttachmentUpdate(Integer worksheetId);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTRESULTS_MUTATE)
    void saveTestResultAttachment(List<Integer> testResultIds, Document document);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTRESULTS)
    TestResult getTestResultById(Integer id);
    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTRESULTS)
    TestResult getTestResultByUuid(String uuid);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTRESULTS)
    List<TestResult> getTestResultByWorksheetItem(String worksheetItemUuid);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTRESULTS)
    List<TestResult> getTestResultByTestRequestItemSample(String testRequestItemSampleUuid);

    // You need to be able to see test results before approving them
    // Specific privileges are verified from inside
    @Transactional
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTRESULTS)
    ApprovalDTO approveTestResultItem(TestRequestAction testRequestAction);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTRESULTS)
    Result<TestApprovalDTO> findTestApprovals(TestApprovalSearchFilter filter);

    @Transactional(readOnly = true)
    DashboardMetricsDTO getDashboardMetrics(Date startDate, Date endDate);

    @Transactional
    @Authorized(value = Privileges.TASK_LABMANAGEMENT_REPORTS_MUTATE, requireAll = false)
    BatchJobDTO saveBatchJob(BatchJobDTO batchJobDTO);

    @Transactional(readOnly = true)
    @Authorized(value = Privileges.APP_LABMANAGEMENT_REPORTS)
    Result<BatchJobDTO> findBatchJobs(BatchJobSearchFilter batchJobSearchFilter);

    @Transactional
    @Authorized(value = Privileges.TASK_LABMANAGEMENT_REPORTS_MUTATE, requireAll = false)
    void cancelBatchJob(String batchJobUuid, String reason);

    @Transactional
    @Authorized(value = Privileges.TASK_LABMANAGEMENT_REPORTS_MUTATE, requireAll = false)
    void failBatchJob(String batchJobUuid, String reason);

    @Transactional
    @Authorized(value = Privileges.TASK_LABMANAGEMENT_REPORTS_MUTATE, requireAll = false)
    void expireBatchJob(String batchJobUuid, String reason);

    @Transactional(readOnly = true)
    @Authorized(value = Privileges.APP_LABMANAGEMENT_REPORTS)
    List<Report> getReports();

    @Transactional(readOnly = true)
    BatchJob getNextActiveBatchJob();

    @Transactional(readOnly = true)
    BatchJob getBatchJobByUuid(String batchJobUuid);

    @Transactional
    void saveBatchJob(BatchJob batchJob);

    @Transactional
    void updateBatchJobRunning(String batchJobUuid);

    @Transactional
    void updateBatchJobExecutionState(String batchJobUuid, String executionState);

    @Transactional(readOnly = true)
    List<BatchJob> getExpiredBatchJobs();

    @Transactional
    void deleteBatchJob(BatchJob batchJob);

    @Transactional(readOnly = true)
    List<Order> getOrdersToMigrate(Integer laboratoryEncounterTypeId, Integer afterOrderId, int limit, Date startDate, Date endDate);

    @Transactional
    Pair<Boolean, String> migrateOrder(Order order);

    Result<TestRequestReportItem> findTestRequestReportItems(TestRequestReportItemFilter filter);

    Map<Integer, List<ObsDto>> getObservations(List<Integer> orderIds);

    List<SummarizedTestReportItem> getSummarizedTestReport(Date startDate, Date endDate, Integer diagnosticLocation);

    List<SummarizedTestReportItem> getIndividualPerformanceReport(Date startDate, Date endDate, Integer diagnosticLocation, Integer userId);

    Result<TestRequestReportItem> findTurnAroundTestRequestReportItems(TestRequestReportItemFilter filter);

    Result<TestRequestReportItem> findAuditReportReportItems(TestRequestReportItemFilter filter);

    Result<TestRequestReportItem> findSampleCustodyReportItems(TestRequestReportItemFilter filter);

    List<SampleActivityDTO> getSampleActivitiesForReport(List<Integer> sampleIds);

    @Transactional(readOnly = true)
    @Authorized(Privileges.APP_LABMANAGEMENT_TESTRESULTS)
    TestResultImportConfig getTestResultImportConfigByHeaderHash(String headerHash, Concept test);

    @Transactional(readOnly = true)
    TestConfig getTestConfigByConcept(Integer conceptId);

    @Transactional
    @Authorized(Privileges.TASK_LABMANAGEMENT_TESTRESULTS_MUTATE)
    TestResultImportConfig saveTestResultImportConfig(TestResultImportConfig testResultImportConfig);

    @Transactional(readOnly = true)
    @Authorized(value = { Privileges.APP_LABMANAGEMENT_STORAGE, Privileges.APP_LABMANAGEMENT_SAMPLES }, requireAll = false)
    Result<StorageUnitDTO> findStorageUnits(StorageSearchFilter filter);

    @Transactional(readOnly = true)
    @Authorized(value = { Privileges.APP_LABMANAGEMENT_STORAGE, Privileges.APP_LABMANAGEMENT_SAMPLES }, requireAll = false)
    Result<StorageDTO> findStorages(StorageSearchFilter filter);

    @Transactional
    @Authorized(value = Privileges.TASK_LABMANAGEMENT_STORAGE_MUTATE)
    Storage saveStorage(StorageDTO storageDTO);

    @Transactional
    @Authorized(value = Privileges.TASK_LABMANAGEMENT_STORAGE_MUTATE)
    void  deleteStorage(String storageUuid);

    @Transactional(readOnly = true)
    @Authorized(value = Privileges.APP_LABMANAGEMENT_SAMPLES)
    Sample getSampleByUuid(String sampleUuid);

    @Transactional
    @Authorized(value = {Privileges.TASK_LABMANAGEMENT_TESTRESULTS_MUTATE, Privileges.TASK_LABMANAGEMENT_REPOSITORY_MUTATE})
    ApprovalDTO disposeSamples(TestRequestAction testRequestAction);

    @Transactional
    @Authorized(value = {Privileges.TASK_LABMANAGEMENT_REPOSITORY_MUTATE})
    ApprovalDTO archiveSamples(TestRequestAction testRequestAction);

    @Transactional
    @Authorized(value = {Privileges.TASK_LABMANAGEMENT_REPOSITORY_MUTATE})
    ApprovalDTO checkOutSamples(TestRequestAction testRequestAction);

    @Transactional(readOnly = true)
    @Authorized(value = { Privileges.APP_LABMANAGEMENT_STORAGE, Privileges.APP_LABMANAGEMENT_SAMPLES }, requireAll = false)
    Storage getStorageByUuid(String uuid);

    @Transactional(readOnly = true)
    @Authorized(value = { Privileges.APP_LABMANAGEMENT_STORAGE, Privileges.APP_LABMANAGEMENT_SAMPLES }, requireAll = false)
    Result<SampleActivityDTO> findSampleActivities(SampleActivitySearchFilter filter);

    @Transactional(readOnly = true)
    @Authorized(value = { Privileges.APP_LABMANAGEMENT_STORAGE, Privileges.APP_LABMANAGEMENT_SAMPLES }, requireAll = false)
    SampleActivity getSampleActivityById(Integer id);

    @Transactional(readOnly = true)
    @Authorized(value = {Privileges.APP_LABMANAGEMENT_TESTRESULTS})
    Document getDocumentById(Integer id);
}
