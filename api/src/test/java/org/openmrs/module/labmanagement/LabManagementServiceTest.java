/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * <p>
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.labmanagement;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.labmanagement.api.LabManagementException;
import org.openmrs.module.labmanagement.api.dao.LabManagementDao;
import org.openmrs.module.labmanagement.api.dto.*;
import org.openmrs.module.labmanagement.api.impl.LabManagementServiceImpl;
import org.openmrs.module.labmanagement.api.jobs.AsyncTasksBatchJob;
import org.openmrs.module.labmanagement.api.jobs.TestConfigImportJob;
import org.openmrs.module.labmanagement.api.model.*;
import org.openmrs.module.labmanagement.api.utils.DateUtil;
import org.openmrs.module.labmanagement.api.utils.GlobalProperties;
import org.openmrs.module.labmanagement.api.utils.StringUtils;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import org.openmrs.*;

/**
 * This is a unit test, which verifies logic in LabManagementService. It doesn't extend
 * BaseModuleContextSensitiveTest, thus it is run without the in-memory DB and Spring context.
 */
public class LabManagementServiceTest extends BaseModuleContextSensitiveTest {

	@InjectMocks
	LabManagementServiceImpl labManagementService;

	@Autowired
	DbSessionFactory sessionFactory;

	private LabManagementDao daoInstance;

	private EntityUtil entityUtil;

	private LabManagementDao dao() {
		if (daoInstance == null) {
			daoInstance = new LabManagementDao();
			daoInstance.setSessionFactory(sessionFactory);
		}
		return daoInstance;
	}

	@Before
	public void setup() throws Exception {
		initializeInMemoryDatabase();
		labManagementService.setDao(dao());
		executeDataSet(EntityUtil.TEST_DATA);
	}

	private EntityUtil eu() {
		if (entityUtil == null) {
			Drug drug = Context.getConceptService().getDrug(3);
			Concept concept = drug.getConcept();
			Role role = Context.getUserService().getRole("Provider");
			Patient patient = Context.getPatientService().getPatient(2);
			User user = Context.getUserService().getUser(501);
			Location location = Context.getLocationService().getLocation(1);
			entityUtil = new EntityUtil(drug, user, location, role, concept, patient);
		}
		return entityUtil;
	}

	//	@Mock
	//	LabManagementDao dao;
	//
	//	@Mock
	//	UserService userService;

	@Before
	public void setupMocks() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void findTestConfigurations_shouldSucceedOnAllFilters() {
		TestConfig testConfig = eu().newTestConfig(dao());
		TestConfigDTO dto = new TestConfigDTO();
		dto.setRequireApproval(testConfig.getRequireApproval());
		dto.setEnabled(testConfig.getEnabled());
		dto.setTestUuid(testConfig.getTest().getUuid());
		dto.setTestGroupUuid(testConfig.getTestGroup().getUuid());
		dto.setApprovalFlowUuid(testConfig.getApprovalFlow().getUuid());
		TestConfig entity = labManagementService.saveTestConfig(dto);
		Context.flushSession();
		Context.flushSession();

		TestConfigSearchFilter filter = new TestConfigSearchFilter();
		filter.setTestConfigId(entity.getId());
		Result<TestConfigDTO> result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
        assertEquals(result.getData().size(), 1);

		filter = new TestConfigSearchFilter();
		filter.setTestConfigUuid(entity.getUuid());
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);

		filter = new TestConfigSearchFilter();
		filter.setTestGroupId(entity.getTestGroup().getId());
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getTestGroupId().equals(entity.getTestGroup().getId())));
		assertFalse(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setTestGroupUuid(entity.getTestGroup().getUuid());
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getTestGroupUuid().equals(entity.getTestGroup().getUuid())));
		assertFalse(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setActive(entity.getEnabled());
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getEnabled().equals(entity.getEnabled())));
		assertFalse(result.getData().isEmpty());

		filter.setActive(!entity.getEnabled());
		result = labManagementService.findTestConfigurations(filter);
        assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getEnabled().equals(entity.getEnabled())));
        assertTrue(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setTestId(entity.getTest().getId());
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getTestId().equals(entity.getTest().getId())));
		assertFalse(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setTestUuid(entity.getTest().getUuid());
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getTestUuid().equals(entity.getTest().getUuid())));
		assertFalse(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setVoided(!entity.getVoided());
		result = labManagementService.findTestConfigurations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setVoided(entity.getVoided());
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		TestConfigDTO dbDto = result.getData().get(0);
		filter = new TestConfigSearchFilter();
		filter.setSearchText(dbDto.getTestName());
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);
		filter = new TestConfigSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findTestConfigurations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new TestConfigSearchFilter();
		filter.setTestIds(Arrays.asList(entity.getTest().getId(), eu().getRandomInt()));
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setTestIds(Arrays.asList(Integer.MAX_VALUE - 10, eu().getRandomInt()));
		result = labManagementService.findTestConfigurations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setTestOrGroupIds(Arrays.asList(entity.getTestGroup().getId(), eu().getRandomInt()));
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setTestOrGroupIds(Arrays.asList(entity.getTest().getId(), eu().getRandomInt()));
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setTestOrGroupIds(Arrays.asList(Integer.MAX_VALUE- 10, Integer.MAX_VALUE - 30));
		result = labManagementService.findTestConfigurations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setLimit(10);
		result = labManagementService.findTestConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new TestConfigSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findTestConfigurations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		testConfig = eu().newTestConfig(dao());
		dao().saveTestConfig(testConfig);

		Context.flushSession();
		Context.flushSession();
		filter = new TestConfigSearchFilter();
		result = labManagementService.findTestConfigurations(filter);
		assertEquals(result.getData().size(), 2);

	}

	@Test
	public void findApprovalFlow_shouldSucceedOnAllFilters() {
		ApprovalFlow approvalFlow = eu().newApprovalFlow(dao());
		ApprovalFlowDTO dto = new ApprovalFlowDTO();
		dto.setName(approvalFlow.getName());
		dto.setSystemName(approvalFlow.getSystemName());
		dto.setLevelOneUuid(approvalFlow.getLevelOne().getUuid());
		dto.setLevelOneAllowOwner(approvalFlow.getLevelOneAllowOwner());

		dto.setLevelTwoUuid(approvalFlow.getLevelTwo().getUuid());
		dto.setLevelTwoAllowOwner(approvalFlow.getLevelTwoAllowOwner());
		dto.setLevelTwoAllowPrevious(approvalFlow.getLevelTwoAllowPrevious());

		dto.setLevelThreeUuid(approvalFlow.getLevelThree().getUuid());
		dto.setLevelThreeAllowOwner(approvalFlow.getLevelThreeAllowOwner());
		dto.setLevelThreeAllowPrevious(approvalFlow.getLevelThreeAllowPrevious());

		dto.setLevelFourUuid(approvalFlow.getLevelFour().getUuid());
		dto.setLevelFourAllowOwner(approvalFlow.getLevelFourAllowOwner());
		dto.setLevelFourAllowPrevious(approvalFlow.getLevelFourAllowPrevious());

		ApprovalFlow entity = labManagementService.saveApprovalFlow(dto);
		Context.flushSession();
		Context.flushSession();

		ApprovalFlowSearchFilter filter = new ApprovalFlowSearchFilter();
		filter.setApprovalFlowId(entity.getId());
		Result<ApprovalFlowDTO> result = labManagementService.findApprovalFlows(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new ApprovalFlowSearchFilter();
		filter.setApprovalFlowUuid(entity.getUuid());
		result = labManagementService.findApprovalFlows(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);

		filter = new ApprovalFlowSearchFilter();
		filter.setNameOrSystemName(entity.getName());
		result = labManagementService.findApprovalFlows(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getName().equals(entity.getName())));
		assertFalse(result.getData().isEmpty());

		filter = new ApprovalFlowSearchFilter();
		filter.setNameOrSystemName(entity.getSystemName());
		result = labManagementService.findApprovalFlows(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getSystemName().equals(entity.getSystemName())));
		assertFalse(result.getData().isEmpty());

		filter = new ApprovalFlowSearchFilter();
		filter.setVoided(!entity.getVoided());
		result = labManagementService.findApprovalFlows(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		filter = new ApprovalFlowSearchFilter();
		filter.setVoided(entity.getVoided());
		result = labManagementService.findApprovalFlows(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		ApprovalFlowDTO dbDto = result.getData().get(0);
		filter = new ApprovalFlowSearchFilter();
		filter.setSearchText(dbDto.getName());
		result = labManagementService.findApprovalFlows(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);
		filter = new ApprovalFlowSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findApprovalFlows(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new ApprovalFlowSearchFilter();
		filter.setSearchText(dbDto.getSystemName());
		result = labManagementService.findApprovalFlows(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new ApprovalFlowSearchFilter();
		filter.setLimit(10);
		result = labManagementService.findApprovalFlows(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new ApprovalFlowSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findApprovalFlows(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

	}

	@Test
	public void importTestConfigurations_areSuccessfull() throws Exception {
		ApprovalFlow approvalFlow=eu().newApprovalFlow(dao());
		approvalFlow.setSystemName("Default");
		approvalFlow.setVoided(false);
		approvalFlow.setVoidedBy(null);
		approvalFlow.setDateVoided(null);
		approvalFlow.setVoidReason(null);
		labManagementService.saveApprovalFlow(approvalFlow);

		Context.flushSession();
		Context.flushSession();

		URL resource = getClass().getClassLoader().getResource(EntityUtil.TEST_CONFIGURATIONS_IMPORT_CSV);
		Path path = Paths.get(resource.toURI()).toFile().toPath();
		TestConfigImportJob importJob = new TestConfigImportJob(path, false);
		importJob.execute();
		ImportResult importResult = (ImportResult) importJob.getResult();
		if(!importResult.getSuccess()){
			throw new Exception(String.join(", ",importResult.getErrors()));
		}
		assertTrue(importResult.getSuccess());

		Context.flushSession();
		Context.flushSession();

		TestConfigSearchFilter testConfigSearchFilter=new TestConfigSearchFilter();
		Result<TestConfigDTO> testConfigDTOs = dao().findTestConfigurations(testConfigSearchFilter);

		assertEquals(5, testConfigDTOs.getData().size());
		Optional<TestConfigDTO> testConfigOptional = testConfigDTOs.getData().stream().filter(p -> p.getTestId().equals(3)).findFirst();
		assertTrue("Concept 3 test config is present", testConfigOptional.isPresent());
		TestConfigDTO testConfig = testConfigOptional.get();
		assertFalse(testConfig.getEnabled());
		assertTrue(testConfig.getRequireApproval());
		assertEquals(testConfig.getTestName(), "COUGH SYRUP");
		assertEquals(testConfig.getTestGroupId(), (Integer) 88);
		assertNotNull(testConfig.getApprovalFlowId());

		testConfigOptional = testConfigDTOs.getData().stream().filter(p -> p.getTestId().equals(14)).findFirst();
		assertTrue("Concept 14 test config is present", testConfigOptional.isPresent());
		testConfig = testConfigOptional.get();
		assertTrue(testConfig.getEnabled());
		assertFalse(testConfig.getRequireApproval());
		assertEquals(testConfig.getTestName(), "FOLLOWING");
		assertEquals(testConfig.getTestGroupId(), (Integer) 5497);
		assertNull(testConfig.getApprovalFlowId());
	}

	@Test
	public void importTestConfigurations_fail() throws Exception {
		ApprovalFlow approvalFlow=eu().newApprovalFlow(dao());
		approvalFlow.setSystemName("Default");
		approvalFlow.setVoided(false);
		approvalFlow.setVoidedBy(null);
		approvalFlow.setDateVoided(null);
		approvalFlow.setVoidReason(null);
		labManagementService.saveApprovalFlow(approvalFlow);

		Context.flushSession();
		Context.flushSession();

		URL resource = getClass().getClassLoader().getResource(EntityUtil.TEST_CONFIGURATIONS_BAD_IMPORT_CSV);
		Path path = Paths.get(resource.toURI()).toFile().toPath();
		TestConfigImportJob importJob = new TestConfigImportJob(path, false);
		importJob.execute();
		ImportResult importResult = (ImportResult) importJob.getResult();
		if(importResult.getSuccess()){
			throw new Exception("Import is supposed to fail");
		}

	}

	@Test
	public void importTestConfigurations_areSuccessfull2() throws Exception {
		ApprovalFlow approvalFlow=eu().newApprovalFlow(dao());
		approvalFlow.setSystemName("Default");
		approvalFlow.setVoided(false);
		approvalFlow.setVoidedBy(null);
		approvalFlow.setDateVoided(null);
		approvalFlow.setVoidReason(null);
		labManagementService.saveApprovalFlow(approvalFlow);

		Context.flushSession();
		Context.flushSession();

		URL resource = getClass().getClassLoader().getResource(EntityUtil.TEST_CONFIGURATIONS_IMPORT_CSV2);
		Path path = Paths.get(resource.toURI()).toFile().toPath();
		TestConfigImportJob importJob = new TestConfigImportJob(path, true);
		importJob.execute();
		ImportResult importResult = (ImportResult) importJob.getResult();
		if(!importResult.getSuccess()){
			throw new Exception(String.join(", ",importResult.getErrors()));
		}
		assertTrue(importResult.getSuccess());

		importJob = new TestConfigImportJob(path, true);
		importJob.execute();
		importResult = (ImportResult) importJob.getResult();
		if(!importResult.getSuccess()){
			throw new Exception(String.join(", ",importResult.getErrors()));
		}
		assertTrue(importResult.getSuccess());

	}


	@Test
	public void findApprovalConfig_shouldSucceedOnAllFilters() {
		ApprovalConfig approvalConfig = eu().newApprovalConfig(dao());
		ApprovalConfigDTO dto = new ApprovalConfigDTO();
		dto.setApprovalTitle(approvalConfig.getApprovalTitle());
		dto.setPrivilege(approvalConfig.getPrivilege());
		dto.setPendingStatus(approvalConfig.getPendingStatus());
		dto.setReturnedStatus(approvalConfig.getReturnedStatus());
		dto.setRejectedStatus(approvalConfig.getRejectedStatus());
		dto.setApprovedStatus(approvalConfig.getApprovedStatus());

		ApprovalConfig entity = labManagementService.saveApprovalConfig(dto);
		Context.flushSession();
		Context.flushSession();

		ApprovalConfigSearchFilter filter = new ApprovalConfigSearchFilter();
		filter.setApprovalConfigId(entity.getId());
		Result<ApprovalConfigDTO> result = labManagementService.findApprovalConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new ApprovalConfigSearchFilter();
		filter.setApprovalConfigUuid(entity.getUuid());
		result = labManagementService.findApprovalConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);

		filter = new ApprovalConfigSearchFilter();
		filter.setApprovalTitle(entity.getApprovalTitle());
		result = labManagementService.findApprovalConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getApprovalTitle().equals(entity.getApprovalTitle())));
		assertFalse(result.getData().isEmpty());

		filter = new ApprovalConfigSearchFilter();
		filter.setVoided(!entity.getVoided());
		result = labManagementService.findApprovalConfigurations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		filter = new ApprovalConfigSearchFilter();
		filter.setVoided(entity.getVoided());
		result = labManagementService.findApprovalConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		ApprovalConfigDTO dbDto = result.getData().get(0);
		filter = new ApprovalConfigSearchFilter();
		filter.setSearchText(dbDto.getApprovalTitle());
		result = labManagementService.findApprovalConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);
		filter = new ApprovalConfigSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findApprovalConfigurations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new ApprovalConfigSearchFilter();
		filter.setLimit(10);
		result = labManagementService.findApprovalConfigurations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new ApprovalConfigSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findApprovalConfigurations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

	}

	@Test
	public void findReferralLocations_shouldSucceedOnAllFilters() {
		ReferralLocation referralLocation = eu().newReferralLocation(dao());
		ReferralLocationDTO dto = new ReferralLocationDTO();
		dto.setReferrerIn(true);
		dto.setReferrerOut(referralLocation.getReferrerOut());
		dto.setEnabled(referralLocation.getEnabled());
		dto.setConceptUuid(null);
		dto.setPatientUuid(referralLocation.getPatient().getUuid());
		dto.setAcronym(referralLocation.getAcronym());
		dto.setName(referralLocation.getName());
		ReferralLocation entity = labManagementService.saveReferralLocation(dto);
		Context.flushSession();
		Context.flushSession();

		ReferralLocationSearchFilter filter = new ReferralLocationSearchFilter();
		filter.setReferralLocationId(entity.getId());
		Result<ReferralLocationDTO> result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new ReferralLocationSearchFilter();
		filter.setReferralLocationUuid(entity.getUuid());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);



		filter = new ReferralLocationSearchFilter();
		filter.setPatientId(entity.getPatient().getId());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getPatientId().equals(entity.getPatient().getId())));
		assertFalse(result.getData().isEmpty());

		filter = new ReferralLocationSearchFilter();
		filter.setPatientUuid(entity.getPatient().getUuid());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getPatientUuid().equals(entity.getPatient().getUuid())));
		assertFalse(result.getData().isEmpty());

		filter = new ReferralLocationSearchFilter();
		filter.setActive(entity.getEnabled());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getEnabled().equals(entity.getEnabled())));
		assertFalse(result.getData().isEmpty());

		ReferralLocationDTO dbDto = result.getData().get(0);
		filter = new ReferralLocationSearchFilter();
		filter.setSearchText(dbDto.getName());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new ReferralLocationSearchFilter();
		filter.setSearchText(dbDto.getPatientGivenName());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new ReferralLocationSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findReferralLocations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter.setActive(!entity.getEnabled());
		result = labManagementService.findReferralLocations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getEnabled().equals(entity.getEnabled())));
		assertTrue(result.getData().isEmpty());

		filter = new ReferralLocationSearchFilter();
		filter.setReferrerIn(entity.getReferrerIn());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getReferrerIn().equals(entity.getReferrerIn())));
		assertFalse(result.getData().isEmpty());

		filter.setReferrerIn(!entity.getReferrerIn());
		result = labManagementService.findReferralLocations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getReferrerIn().equals(entity.getReferrerIn())));
		assertTrue(result.getData().isEmpty());

		filter = new ReferralLocationSearchFilter();
		filter.setReferrerOut(entity.getReferrerOut());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getReferrerOut().equals(entity.getReferrerOut())));
		assertFalse(result.getData().isEmpty());

		filter.setReferrerOut(!entity.getReferrerOut());
		result = labManagementService.findReferralLocations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getReferrerOut().equals(entity.getReferrerOut())));
		assertTrue(result.getData().isEmpty());

		filter = new ReferralLocationSearchFilter();
		filter.setLimit(10);
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new ReferralLocationSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findReferralLocations(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		dto.setUuid(entity.getUuid());
		dto.setConceptUuid(referralLocation.getConcept().getUuid());
		dto.setName(null);
		ReferralLocation entity2 = labManagementService.saveReferralLocation(dto);
		Context.flushSession();
		Context.flushSession();

		filter = new ReferralLocationSearchFilter();
		filter.setConceptId(entity2.getConcept().getId());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity2.getUuid()) && p.getConceptId().equals(entity2.getConcept().getId())));
		assertFalse(result.getData().isEmpty());

		filter = new ReferralLocationSearchFilter();
		filter.setConceptUuid(entity2.getConcept().getUuid());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity2.getUuid()) && p.getConceptUuid().equals(entity2.getConcept().getUuid())));
		assertFalse(result.getData().isEmpty());

		filter = new ReferralLocationSearchFilter();
		filter.setSearchText(dbDto.getConceptName());
		result = labManagementService.findReferralLocations(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity2.getId())));
		assertEquals(result.getData().size(), 1);

		referralLocation = eu().newReferralLocation(dao());
		dao().saveReferralLocation(referralLocation);

		Context.flushSession();
		Context.flushSession();
		filter = new ReferralLocationSearchFilter();
		result = labManagementService.findReferralLocations(filter);
		assertEquals(result.getData().size(), 2);

	}

	@Test
	public void saveTestRequest_shouldSucceedForReferral() {
		TestRequestDTO testRequestDTO = eu().newLabRequestReferral(dao());
		labManagementService.saveTestRequest(testRequestDTO);
	}

	@Test
	public void saveTestRequest_shouldSucceedForPatient() {
		TestRequestDTO testRequestDTO = eu().newLabRequestPatient(dao());
		labManagementService.saveTestRequest(testRequestDTO);
	}

	@Test
	public void findTestItems_shouldSucceedOnAllFilters() {
		TestRequestDTO testRequestDTO = eu().newLabRequestReferral(dao());
		TestRequest entity = labManagementService.saveTestRequest(testRequestDTO);

		Context.flushSession();
		Context.flushSession();

		TestRequestSearchFilter filter = new TestRequestSearchFilter();
		filter.setTestRequestUuid(entity.getUuid());
		Result<TestRequestDTO> result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);

		filter = new TestRequestSearchFilter();
		filter.setPatientId(entity.getPatient().getId());
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getPatientId().equals(entity.getPatient().getId())));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setReferredOut(testRequestDTO.getSamples().get(0).getTests().get(0).getReferredOut());
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setIncludeTestItems(true);
		filter.setIncludeAllTests(true);
		filter.setIncludeTestRequestItemSamples(true);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) &&
			p.getTests().stream().noneMatch(x-> x.getSamples().isEmpty())
		));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setIncludeTestItems(true);
		filter.setIncludeAllTests(true);
		filter.setIncludeTestRequestItemSamples(false);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) &&
				p.getTests().stream().allMatch(x->x.getSamples() == null)));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		List<Integer> testIds = new ArrayList<>();
		List<String> testNames = new ArrayList<>();

		int testCount = 0;
		int referredOutCount = testRequestDTO.getSamples().stream().map(p->
				p.getTests().stream().map(x-> x.getReferredOut() != null && x.getReferredOut() ? 1 : 0).reduce(0,(a,b)-> a+b))
				.reduce(0,(x,y)-> x+y);
		for(String testUuid :
		testRequestDTO.getSamples().stream().map(p->p.getTests().stream().map(x->x.getTestUuid()))
				.flatMap(x->x)
				.distinct()
				.collect(Collectors.toList())){

			List<Integer> testIds1 = new ArrayList<>();
			testIds1.clear();
			Concept testRequestItem = Context.getConceptService().getConceptByUuid(testUuid);
			testCount++;
			testIds1.add(testRequestItem.getId());
			testIds.add(testRequestItem.getId());
			testNames.add(testRequestItem.getFullySpecifiedName(Context.getLocale()).getName());
			filter.setTestConceptIds(testIds1);
			assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
			assertFalse(result.getData().isEmpty());
		}

		filter = new TestRequestSearchFilter();
		filter.setSearchText(testRequestDTO.getRequestNo());
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new TestRequestSearchFilter();
		filter.setSearchText(entity.getPatient().getGivenName());
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new TestRequestSearchFilter();
		filter.setSearchText(entity.getPatient().getFamilyName());
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		for(String testName: testNames){
			filter = new TestRequestSearchFilter();
			filter.setSearchText(testName.substring(0,Math.max(1,testName.length()/2)));
			result = labManagementService.findTestRequests(filter);
			assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
			assertEquals(result.getData().size(), 1);
		}

		filter = new TestRequestSearchFilter();
		filter.setMinActivatedDate(DateUtil.today());
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new TestRequestSearchFilter();
		filter.setMaxActivatedDate(org.openmrs.util.OpenmrsUtil.getLastMomentOfDay(DateUtil.today()));
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new TestRequestSearchFilter();
		filter.setMinActivatedDate(DateUtils.addDays(DateUtil.today(), 1));
		result = labManagementService.findTestRequests(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new TestRequestSearchFilter();
		filter.setMaxActivatedDate(DateUtils.addDays(DateUtil.today(), -1));
		result = labManagementService.findTestRequests(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new TestRequestSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findTestRequests(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new TestRequestSearchFilter();
		filter.setReferredIn(entity.getReferredIn());
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getReferredIn().equals(entity.getReferredIn())));
		assertFalse(result.getData().isEmpty());

		filter.setReferredIn(!entity.getReferredIn());
		result = labManagementService.findTestRequests(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getReferredIn().equals(entity.getReferredIn())));
		assertTrue(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setReferredOut(referredOutCount > 0);
		result = labManagementService.findTestRequests(filter);
		if(referredOutCount > 0) {
			assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		}else{
			filter.setReferredOut(true);
			result = labManagementService.findTestRequests(filter);
			assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		}

		filter = new TestRequestSearchFilter();
		filter.setPatientId(entity.getPatient().getPatientId());
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && p.getPatientId().equals(entity.getPatient().getId())));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setIncludeTestRequestItemSamples(true);
		filter.setIncludeTestItems(true);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().allMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) &&
				p.getTests().stream().allMatch(x->!x.getSamples().isEmpty())));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setIncludeTestRequestItemSamples(false);
		filter.setIncludeTestItems(true);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().allMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) &&
				p.getTests().stream().allMatch(x->x.getSamples() == null)));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setPatientId(entity.getPatient().getPatientId());
		List<Integer> fetchedTests = testIds.stream().limit(testIds.size() / 2).collect(Collectors.toList());
		filter.setTestConceptIds(fetchedTests);
		filter.setIncludeTestItems(true);
		filter.setIncludeAllTests(false);
		result = labManagementService.findTestRequests(filter);
		assertEquals(result.getData().stream().map(p->p.getTests().stream()
				.map(x->x.getTestUuid())).flatMap(p->p).distinct().count(), fetchedTests.size());
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setPatientId(entity.getPatient().getPatientId());
		filter.setTestConceptIds(fetchedTests);
		filter.setIncludeTestItems(false);
		filter.setIncludeAllTests(false);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().allMatch(p->p.getTests() == null || p.getTests().isEmpty()));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setPatientId(entity.getPatient().getPatientId());
		filter.setTestConceptIds(fetchedTests);
		filter.setIncludeTestItems(true);
		filter.setIncludeAllTests(true);
		result = labManagementService.findTestRequests(filter);
		assertEquals(result.getData().stream().map(p->p.getTests().stream()
				.map(x->x.getTestUuid())).flatMap(p->p).distinct().count(), testIds.size());
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setLimit(10);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findTestRequests(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Integer firstRecordId = entity.getId();
		testRequestDTO = eu().newLabRequestPatient(dao());
		TestRequest entity2 = labManagementService.saveTestRequest(testRequestDTO);
		Context.flushSession();
		Context.flushSession();

		List<SortField> sortOrders = StringUtils.parseSortOrder("+id");
		filter = new TestRequestSearchFilter();
		filter.setSortOrders(sortOrders);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().get(0).getId().equals(firstRecordId));
		assertTrue(result.getData().get(1).getId().equals(entity2.getId()));
		assertFalse(result.getData().isEmpty());

		sortOrders = StringUtils.parseSortOrder("-id");
		filter = new TestRequestSearchFilter();
		filter.setSortOrders(sortOrders);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().get(1).getId().equals(firstRecordId));
		assertTrue(result.getData().get(0).getId().equals(entity2.getId()));
		assertFalse(result.getData().isEmpty());

		sortOrders = StringUtils.parseSortOrder("+dateCreated");
		filter = new TestRequestSearchFilter();
		filter.setSortOrders(sortOrders);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().get(0).getId().equals(firstRecordId));
		assertTrue(result.getData().get(1).getId().equals(entity2.getId()));
		assertFalse(result.getData().isEmpty());

		sortOrders = StringUtils.parseSortOrder("-dateCreated");
		filter = new TestRequestSearchFilter();
		filter.setSortOrders(sortOrders);
		result = labManagementService.findTestRequests(filter);
		assertTrue(result.getData().size() == 2);

		assertTrue(String.format("%1s %2s",  result.getData().get(1).getDateCreated(),  result.getData().get(0).getDateCreated()), result.getData().get(1).getId().equals(firstRecordId));
		assertTrue(String.format("%1s %2s",  result.getData().get(0).getDateCreated(),  result.getData().get(1).getDateCreated()), result.getData().get(0).getId().equals(entity2.getId()));
		assertFalse(result.getData().isEmpty());

		filter = new TestRequestSearchFilter();
		filter.setSampleStatuses(Arrays.asList(SampleStatus.COLLECTION));
		result = labManagementService.findTestRequests(filter);

		filter = new TestRequestSearchFilter();
		filter.setPendingResultApproval(true);
		result = labManagementService.findTestRequests(filter);
		assertEquals(result.getData().size(), 0);

		filter = new TestRequestSearchFilter();
		filter.setOnlyPendingResultApproval(true);
		filter.setPendingResultApproval(true);
		result = labManagementService.findTestRequests(filter);
		assertEquals(result.getData().size(), 0);

		filter = new TestRequestSearchFilter();
		filter.setOnlyPendingResultApproval(true);
		filter.setPendingResultApproval(true);
		filter.setIncludeTestItemTestResult(true);
		result = labManagementService.findTestRequests(filter);
		assertEquals(result.getData().size(), 0);

		filter = new TestRequestSearchFilter();
		filter.setPatientId(entity.getPatient().getPatientId());
		filter.setTestConceptIds(fetchedTests);
		filter.setIncludeTestItems(true);
		filter.setIncludeAllTests(true);
		filter.setIncludeTestItemWorksheetInfo(true);
		result = labManagementService.findTestRequests(filter);
		assertEquals(result.getData().stream().map(p->p.getTests().stream()
				.map(x->x.getTestUuid())).flatMap(p->p).distinct().count(), testIds.size());
		assertFalse(result.getData().isEmpty());

	}

	private  void  SetGlobalProperties(){
		GlobalProperty globalProperty=new GlobalProperty();
		globalProperty.setProperty(GlobalProperties.OWNER_CAN_APPROVE_TEST_REQUESTS);
		globalProperty.setPropertyValue("true");
		globalProperty.setDescription("OWNER_CAN_APPROVE_TEST_REQUESTS");
		GlobalProperties.saveGlobalProperty(globalProperty);

		globalProperty=new GlobalProperty();
		globalProperty.setProperty(GlobalProperties.REQUIRE_TEST_REQUEST_APPROVAL);
		globalProperty.setPropertyValue("true");
		globalProperty.setDescription("REQUIRE_TEST_REQUEST_APPROVAL");
		GlobalProperties.saveGlobalProperty(globalProperty);

		globalProperty=new GlobalProperty();
		globalProperty.setProperty(GlobalProperties.REQUIRE_REFERRAL_TEST_REQUEST_APPROVAL);
		globalProperty.setPropertyValue("true");
		globalProperty.setDescription("REQUIRE_TEST_REQUEST_APPROVAL");
		GlobalProperties.saveGlobalProperty(globalProperty);
	}

	@Test
	public void approveTestRequest_shouldSucceed() {
		SetGlobalProperties();
		TestRequestDTO testRequestDTO = eu().newLabRequestReferral(dao());
		TestRequest entity = labManagementService.saveTestRequest(testRequestDTO);
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.REQUEST_APPROVAL);
		}

		TestRequestAction testRequestAction =new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Approved");
		testRequestAction.setRecords(dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false).stream().map(BaseOpenmrsObject::getUuid).collect(Collectors.toList()));
		ApprovalDTO approvalDTO = labManagementService.approveTestRequestItem(testRequestAction);

		entity = labManagementService.getTestRequestById(entity.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
                Collections.singletonList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.IN_PROGRESS);
		}

		testRequestDTO = eu().newLabRequestPatient(dao());
		entity = labManagementService.saveTestRequest(testRequestDTO);
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
                Collections.singletonList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.REQUEST_APPROVAL);
		}

		testRequestAction =new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Approved");
		testRequestAction.setRecords(dao().getTestRequestItemsByTestRequestId(
                Collections.singletonList(entity.getId()), false).stream().map(BaseOpenmrsObject::getUuid).collect(Collectors.toList()));
		approvalDTO = labManagementService.approveTestRequestItem(testRequestAction);

		entity = labManagementService.getTestRequestById(entity.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
                Collections.singletonList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.SAMPLE_COLLECTION);
		}

		testRequestAction =new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Rejected");
		testRequestAction.setRecords(dao().getTestRequestItemsByTestRequestId(
				Collections.singletonList(entity.getId()), false).stream().map(BaseOpenmrsObject::getUuid).collect(Collectors.toList()));
		TestRequestAction finalTestRequestAction = testRequestAction;
		Assert.assertThrows(LabManagementException.class, ()-> labManagementService.approveTestRequestItem(finalTestRequestAction));

		testRequestAction.setAction(ApprovalResult.REJECTED);
		approvalDTO = labManagementService.approveTestRequestItem(testRequestAction);

		entity = labManagementService.getTestRequestById(entity.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.CANCELLED);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.CANCELLED);
		}

		testRequestDTO = eu().newLabRequestPatient(dao());
		entity = labManagementService.saveTestRequest(testRequestDTO);
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
                Collections.singletonList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.REQUEST_APPROVAL);
		}

		testRequestAction =new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.REJECTED);
		testRequestAction.setRemarks("Rejected");
		testRequestAction.setRecords(dao().getTestRequestItemsByTestRequestId(
                Collections.singletonList(entity.getId()), false).stream().map(BaseOpenmrsObject::getUuid).collect(Collectors.toList()));
		approvalDTO = labManagementService.approveTestRequestItem(testRequestAction);

		entity = labManagementService.getTestRequestById(entity.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.CANCELLED);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.CANCELLED);
		}

		Context.flushSession();
		Context.flushSession();
	}

	@Test
	public void findSamples_shouldSucceedOnAllFilters() {
		TestRequestDTO testRequestDTO = eu().newLabRequestReferral(dao());
		TestRequest testRequest = labManagementService.saveTestRequest(testRequestDTO);

		Context.flushSession();
		Context.flushSession();
		Context.updateSearchIndexForType(Sample.class);

		SampleSearchFilter filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setTestRequestId(testRequest.getId());
		Result<SampleDTO> result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().allMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		assertEquals(result.getData().size(), testRequestDTO.getSamples().size());
		List<SampleDTO> sampleIds = result.getData();

		filter = new SampleSearchFilter();
		filter.setPatientId(testRequest.getPatient().getId());
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid()) && p.getPatientId().equals(testRequest.getPatient().getId())));
		assertFalse(result.getData().isEmpty());


		filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setTestItemlocationId( Context.getLocationService().getLocationByUuid(testRequestDTO.getSamples().stream().map(x->x.getTests().get(0).getToLocationUuid()).findFirst().orElse(null)).getLocationId());
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid()) && p.getPatientId().equals(testRequest.getPatient().getId())));
		assertFalse(result.getData().isEmpty());
		assertTrue(result.getData().stream().anyMatch(p->p.getTests() != null && !p.getTests().isEmpty()));

		filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setTestRequestItemConceptIds( testRequestDTO.getSamples().stream().flatMap(p-> p.getTests().stream().map(x-> {
			Concept concept = Context.getConceptService().getConceptByUuid(x.getTestUuid());
			return concept.getId();
		})).distinct().collect(Collectors.toList()));
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid()) && p.getPatientId().equals(testRequest.getPatient().getId())));
		assertFalse(result.getData().isEmpty());
		assertTrue(result.getData().stream().anyMatch(p->p.getTests() != null && !p.getTests().isEmpty()));

		filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setTestItemlocationId( Context.getLocationService().getLocationByUuid(testRequestDTO.getSamples().stream().map(x->x.getTests().get(0).getToLocationUuid()).findFirst().orElse(null)).getLocationId());
		filter.setTestRequestItemConceptIds( testRequestDTO.getSamples().stream().flatMap(p-> p.getTests().stream().map(x-> {
			Concept concept = Context.getConceptService().getConceptByUuid(x.getTestUuid());
			return concept.getId();
		})).distinct().collect(Collectors.toList()));
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid()) && p.getPatientId().equals(testRequest.getPatient().getId())));
		assertFalse(result.getData().isEmpty());
		assertTrue(result.getData().stream().anyMatch(p->p.getTests() != null && !p.getTests().isEmpty()));

		filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setAllTests(true);
		filter.setTestItemlocationId( Context.getLocationService().getLocationByUuid(testRequestDTO.getSamples().stream().map(x->x.getTests().get(0).getToLocationUuid()).findFirst().orElse(null)).getLocationId());
		filter.setTestRequestItemConceptIds( testRequestDTO.getSamples().stream().flatMap(p-> p.getTests().stream().map(x-> {
			Concept concept = Context.getConceptService().getConceptByUuid(x.getTestUuid());
			return concept.getId();
		})).distinct().collect(Collectors.toList()));
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid()) && p.getPatientId().equals(testRequest.getPatient().getId())));
		assertFalse(result.getData().isEmpty());
		assertTrue(result.getData().stream().anyMatch(p->p.getTests() != null && !p.getTests().isEmpty()));

		filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setAllTests(false);
		filter.setTestItemlocationId( Context.getLocationService().getLocationByUuid(testRequestDTO.getSamples().stream().map(x->x.getTests().get(0).getToLocationUuid()).findFirst().orElse(null)).getLocationId());
		filter.setTestRequestItemConceptIds( testRequestDTO.getSamples().stream().flatMap(p-> p.getTests().stream().map(x-> {
			Concept concept = Context.getConceptService().getConceptByUuid(x.getTestUuid());
			return concept.getId();
		})).distinct().collect(Collectors.toList()));
		filter.setTestRequestItemStatuses(Arrays.stream(TestRequestItemStatus.SAMPLE_COLLECTION.getDeclaringClass().getEnumConstants()).map(p-> (TestRequestItemStatus)p).collect(Collectors.toList()));
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid()) && p.getPatientId().equals(testRequest.getPatient().getId())));
		assertFalse(result.getData().isEmpty());
		assertTrue(result.getData().stream().anyMatch(p->p.getTests() != null && !p.getTests().isEmpty()));

		for(SampleDTO sample : sampleIds) {
			filter = new SampleSearchFilter();
			filter.setSampleId(sample.getId());
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().allMatch(p -> p.getId().equals(sample.getId())));
			assertFalse(result.getData().isEmpty());

			filter = new SampleSearchFilter();
			filter.setSampleUuid(sample.getUuid());
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().allMatch(p -> p.getUuid().equals(sample.getUuid())));
			assertFalse(result.getData().isEmpty());

			filter = new SampleSearchFilter();
			filter.setSearchText(sample.getAccessionNumber());
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().allMatch(p -> p.getId().equals(sample.getId()) && p.getAccessionNumber().equals(sample.getAccessionNumber())));
			assertEquals(result.getData().size(), 1);

			filter = new SampleSearchFilter();
			filter.setSearchText(sample.getExternalRef());
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().allMatch(p -> p.getId().equals(sample.getId()) && p.getExternalRef().equals(sample.getExternalRef())));
			assertEquals(result.getData().size(), 1);

			filter = new SampleSearchFilter();
			filter.setReference(sample.getAccessionNumber());
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().allMatch(p -> p.getId().equals(sample.getId()) && p.getAccessionNumber().equals(sample.getAccessionNumber())));
			assertEquals(result.getData().size(), 1);

			filter = new SampleSearchFilter();
			filter.setReference(sample.getExternalRef());
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().allMatch(p -> p.getId().equals(sample.getId()) && p.getExternalRef().equals(sample.getExternalRef())));
			assertEquals(result.getData().size(), 1);

			filter = new SampleSearchFilter();
			filter.setSampleTypeId(sample.getSampleTypeId());
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(sample.getId())));
			assertTrue(!result.getData().isEmpty());

			filter = new SampleSearchFilter();
			filter.setSampleStatuses(Arrays.asList(sample.getStatus()));
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(sample.getId())));
			assertTrue(!result.getData().isEmpty());

			filter = new SampleSearchFilter();
			Location location = Context.getLocationService().getLocationByUuid(sample.getAtLocationUuid());
			filter.setLocationId(location.getId());
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(sample.getId())));
			assertTrue(!result.getData().isEmpty());

			Sample entitySample = dao().getSampleById(sample.getId());
			entitySample.setCollectedBy(Context.getAuthenticatedUser());
			entitySample.setCollectionDate(new Date());
			entitySample = dao().saveSample(entitySample);
			Context.flushSession();
			Context.flushSession();

			filter = new SampleSearchFilter();
			filter.setMinCollectionDate(DateUtil.today());
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equals(testRequest.getUuid())));
			assertTrue(result.getData().size() > 0);

			filter = new SampleSearchFilter();
			filter.setMaxCollectionDate(org.openmrs.util.OpenmrsUtil.getLastMomentOfDay(DateUtil.today()));
			result = labManagementService.findSamples(filter);
			assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equals(testRequest.getUuid())));
			assertTrue(result.getData().size() > 0);

			filter = new SampleSearchFilter();
			filter.setMinCollectionDate(DateUtils.addDays(DateUtil.today(), 1));
			result = labManagementService.findSamples(filter);
			assertFalse(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equals(testRequest.getUuid())));
			assertEquals(result.getData().size(), 0);

			filter = new SampleSearchFilter();
			filter.setMaxCollectionDate(DateUtils.addDays(DateUtil.today(), -1));
			result = labManagementService.findSamples(filter);
			assertFalse(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equals(testRequest.getUuid())));
			assertEquals(result.getData().size(), 0);

		}

		filter = new SampleSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findSamples(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(testRequest.getId())));
		assertEquals(result.getData().size(), 0);

		Sample sampleInStorage = eu().newSample(dao());
		sampleInStorage.setStatus(SampleStatus.ARCHIVED);
		sampleInStorage = dao().saveSample(sampleInStorage);
		Context.flushSession();
		Context.flushSession();

		filter = new SampleSearchFilter();
		filter.setTestRequestId(testRequest.getId());
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().allMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		assertFalse(result.getData().isEmpty());
		filter.setIncludeSamplesInStorage(true);
		filter.setPatientId(testRequest.getPatient().getPatientId());
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		Sample finalSampleInStorage = sampleInStorage;
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(finalSampleInStorage.getId())));
		assertFalse(result.getData().isEmpty());

		filter = new SampleSearchFilter();
		filter.setLimit(10);
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		assertFalse(result.getData().isEmpty());

		filter = new SampleSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findSamples(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		assertTrue(result.getData().isEmpty());

		filter = new SampleSearchFilter();
		filter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		assertFalse(result.getData().isEmpty());
	}

	@Test
	public void saveSample_shouldSucceed(){
		SetGlobalProperties();
		TestRequestDTO testRequestDTO = eu().newLabRequestPatient(dao());
		TestRequest entity = labManagementService.saveTestRequest(testRequestDTO);
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		List<TestRequestItem> testRequestItems = dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false);
		for(TestRequestItem testRequestItem: testRequestItems){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.REQUEST_APPROVAL);
		}

		TestRequestAction testRequestAction =new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Approved");
		testRequestAction.setRecords(dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false).stream().map(BaseOpenmrsObject::getUuid).collect(Collectors.toList()));
		ApprovalDTO approvalDTO = labManagementService.approveTestRequestItem(testRequestAction);

		entity = labManagementService.getTestRequestById(entity.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
				Collections.singletonList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.SAMPLE_COLLECTION);
		}

		List<String> testItemUuids = testRequestAction.getRecords();
		SampleDTO sampleDTO1 = eu().newLabSample(dao(),entity.getUuid(), testItemUuids.stream().limit(2).collect(Collectors.toList()));
		Sample sample1 = labManagementService.saveSample(sampleDTO1).getValue1();
		sample1 = dao().getSampleById(sample1.getId());
		List<TestRequestItemSample> testRequestItemSamples1 = dao().getTestRequestItemSamples(sample1);
		Assert.assertTrue(testRequestItemSamples1.stream().allMatch(p->
				sampleDTO1.getSampleTestItemUuids().contains(p.getTestRequestItem().getUuid())));


		SampleDTO sampleDTO2 = eu().newLabSample(dao(),entity.getUuid(), testItemUuids.stream().skip(2).collect(Collectors.toList()));
		Sample sample2 = labManagementService.saveSample(sampleDTO2).getValue1();
		sample2 = dao().getSampleById(sample2.getId());
		List<TestRequestItemSample> testRequestItemSamples2 = dao().getTestRequestItemSamples(sample2);
		Assert.assertTrue(testRequestItemSamples2.stream().allMatch(p->
				sampleDTO2.getSampleTestItemUuids().contains(p.getTestRequestItem().getUuid())));


		TestRequestSearchFilter testRequestSearchFilter=new TestRequestSearchFilter();
		testRequestSearchFilter.setIncludeTestItems(true);
		testRequestSearchFilter.setItemStatuses(Collections.singletonList(TestRequestItemStatus.SAMPLE_COLLECTION));
		testRequestSearchFilter.setIncludeTestRequestItemSamples(true);
		List<TestRequestDTO>  testRequests = labManagementService.findTestRequests(testRequestSearchFilter).getData();
		TestRequest finalEntity = entity;
		Optional<TestRequestDTO> testRequestOptional = testRequests.stream().filter(p->p.getUuid().equalsIgnoreCase(finalEntity.getUuid())).findFirst();
		Assert.assertTrue(testRequestOptional.isPresent());
		TestRequestDTO testRequest = testRequestOptional.get();
		List<Sample> samples = dao().getSamplesByTestRequest(entity);
		Sample finalSample = sample2;
		Sample finalSample1 = sample1;
		Assert.assertTrue(samples.stream().allMatch(p->
				p.getUuid().equalsIgnoreCase(finalSample1.getUuid()) ||
						p.getUuid().equalsIgnoreCase(finalSample.getUuid()))
		);



		testRequestDTO = eu().newLabRequestPatient(dao());
		entity = labManagementService.saveTestRequest(testRequestDTO);
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		testRequestItems = dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false);
		for(TestRequestItem testRequestItem: testRequestItems){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.REQUEST_APPROVAL);
		}

		testRequestAction =new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Approved");
		testRequestAction.setRecords(dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false).stream().map(BaseOpenmrsObject::getUuid).collect(Collectors.toList()));
		approvalDTO = labManagementService.approveTestRequestItem(testRequestAction);

		entity = labManagementService.getTestRequestById(entity.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
				Collections.singletonList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.SAMPLE_COLLECTION);
		}

		/*
		* accessionNumber	"LTFDBA"
atLocationUuid	"c8b19970-087a-4ed9-9054-c40ba8015358"
referredOut	false
sampleTypeUuid	"162403AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
testRequestUuid	"a36f54c1-a696-4283-b5b4-e528016c9336"
tests	[…]
0	"dd1dc17c-20dd-450e-83a2-ebc906d2d3d1"
		* */

		testItemUuids = testRequestAction.getRecords();
		SampleDTO sampleDTO = new SampleDTO();
		sampleDTO.setSampleTypeUuid(eu().getConcept().getUuid());
		sampleDTO.setAccessionNumber(eu().getRandomString(8));
		sampleDTO.setAtLocationUuid(eu().getLocation().getUuid());
		sampleDTO.setReferredOut(false);
		sampleDTO.setTestRequestUuid(entity.getUuid());
		sampleDTO.setSampleTestItemUuids(new HashSet<>(testItemUuids));

		sample1 = labManagementService.saveSample(sampleDTO).getValue1();
		sample1 = dao().getSampleById(sample1.getId());
		testRequestItemSamples1 = dao().getTestRequestItemSamples(sample1);
		Assert.assertTrue(testRequestItemSamples1.stream().allMatch(p->
				sampleDTO.getSampleTestItemUuids().contains(p.getTestRequestItem().getUuid())));

		sampleDTO.setUuid(sample1.getUuid());
		sample1 = labManagementService.saveSample(sampleDTO).getValue1();
		sample1 = dao().getSampleById(sample1.getId());
		testRequestItemSamples1 = dao().getTestRequestItemSamples(sample1);
		Assert.assertTrue(testRequestItemSamples1.stream().allMatch(p->
				sampleDTO.getSampleTestItemUuids().contains(p.getTestRequestItem().getUuid())));

	}

	@Test
	public void saveSampleWithReferral_shouldSucceed(){
		SetGlobalProperties();
		TestRequestDTO testRequestDTO = eu().newLabRequestPatient(dao());
		TestRequest entity = labManagementService.saveTestRequest(testRequestDTO);
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		List<TestRequestItem> testRequestItems = dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false);
		for(TestRequestItem testRequestItem: testRequestItems){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.REQUEST_APPROVAL);
		}

		TestRequestAction testRequestAction =new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Approved");
		testRequestAction.setRecords(dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false).stream().map(BaseOpenmrsObject::getUuid).collect(Collectors.toList()));
		ApprovalDTO approvalDTO = labManagementService.approveTestRequestItem(testRequestAction);

		entity = labManagementService.getTestRequestById(entity.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
				Collections.singletonList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.SAMPLE_COLLECTION);
		}

		List<String> testItemUuids = testRequestAction.getRecords();
		SampleDTO sampleDTO1 = eu().newLabSample(dao(),entity.getUuid(), testItemUuids.stream().limit(2).collect(Collectors.toList()));
		Sample sample1 = labManagementService.saveSample(sampleDTO1).getValue1();
		sample1 = dao().getSampleById(sample1.getId());
		List<TestRequestItemSample> testRequestItemSamples1 = dao().getTestRequestItemSamples(sample1);
		Assert.assertTrue(testRequestItemSamples1.stream().allMatch(p->
				sampleDTO1.getSampleTestItemUuids().contains(p.getTestRequestItem().getUuid())));


		SampleDTO sampleDTO2 = eu().newLabSampleWithReferral(dao(),entity.getUuid(), testItemUuids.stream().skip(2).collect(Collectors.toList()));
		Sample sample2 = labManagementService.saveSample(sampleDTO2).getValue1();
		sample2 = dao().getSampleById(sample2.getId());
		List<TestRequestItemSample> testRequestItemSamples2 = dao().getTestRequestItemSamples(sample2);
		Assert.assertTrue(testRequestItemSamples2.stream().allMatch(p->
				sampleDTO2.getSampleTestItemUuids().contains(p.getTestRequestItem().getUuid())));


		TestRequestSearchFilter testRequestSearchFilter=new TestRequestSearchFilter();
		testRequestSearchFilter.setIncludeTestItems(true);
		testRequestSearchFilter.setItemStatuses(Collections.singletonList(TestRequestItemStatus.SAMPLE_COLLECTION));
		testRequestSearchFilter.setIncludeTestRequestItemSamples(true);
		List<TestRequestDTO>  testRequests = labManagementService.findTestRequests(testRequestSearchFilter).getData();
		TestRequest finalEntity = entity;
		Optional<TestRequestDTO> testRequestOptional = testRequests.stream().filter(p->p.getUuid().equalsIgnoreCase(finalEntity.getUuid())).findFirst();
		Assert.assertTrue(testRequestOptional.isPresent());
		TestRequestDTO testRequest = testRequestOptional.get();
		List<Sample> samples = dao().getSamplesByTestRequest(entity);
		Sample finalSample = sample2;
		Sample finalSample1 = sample1;
		Assert.assertTrue(samples.stream().allMatch(p->
				p.getUuid().equalsIgnoreCase(finalSample1.getUuid()) ||
						p.getUuid().equalsIgnoreCase(finalSample.getUuid()))
		);

	}

	@Test
	public void releaseSampleForTesting_shouldSucceed(){
		SetGlobalProperties();

		TestRequestDTO testRequestDTO = eu().newLabRequestPatient(dao());
		TestRequest entity = labManagementService.saveTestRequest(testRequestDTO);
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		List<TestRequestItem> testRequestItems = dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false);
		for(TestRequestItem testRequestItem: testRequestItems){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.REQUEST_APPROVAL);
		}

		TestRequestAction testRequestAction =new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Approved");
		testRequestAction.setRecords(dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false).stream().map(BaseOpenmrsObject::getUuid).collect(Collectors.toList()));
		ApprovalDTO approvalDTO = labManagementService.approveTestRequestItem(testRequestAction);

		entity = labManagementService.getTestRequestById(entity.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
		for(TestRequestItem testRequestItem: dao().getTestRequestItemsByTestRequestId(
				Collections.singletonList(entity.getId()), false)){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.SAMPLE_COLLECTION);
		}

		List<String> testItemUuids = testRequestAction.getRecords();
		SampleDTO sampleDTO = new SampleDTO();
		sampleDTO.setSampleTypeUuid(eu().getConcept().getUuid());
		sampleDTO.setAccessionNumber(eu().getRandomString(8));
		sampleDTO.setAtLocationUuid(eu().getLocation().getUuid());
		sampleDTO.setReferredOut(true);
		ReferralLocation referralLocation = eu().newReferralLocation(dao());
		referralLocation = dao().saveReferralLocation(referralLocation);
		sampleDTO.setReferralToFacilityUuid(referralLocation.getUuid());
		sampleDTO.setTestRequestUuid(entity.getUuid());
		sampleDTO.setSampleTestItemUuids(new HashSet<>(testItemUuids));

		Sample sample1 = labManagementService.saveSample(sampleDTO).getValue1();
		sample1 = dao().getSampleById(sample1.getId());
		List<TestRequestItemSample> testRequestItemSamples1 = dao().getTestRequestItemSamples(sample1);
		Assert.assertTrue(testRequestItemSamples1.stream().allMatch(p->
				sampleDTO.getSampleTestItemUuids().contains(p.getTestRequestItem().getUuid())));

		sampleDTO.setUuid(sample1.getUuid());
		sample1 = labManagementService.saveSample(sampleDTO).getValue1();
		sample1 = dao().getSampleById(sample1.getId());
		testRequestItemSamples1 = dao().getTestRequestItemSamples(sample1);
		Assert.assertTrue(testRequestItemSamples1.stream().allMatch(p->
				sampleDTO.getSampleTestItemUuids().contains(p.getTestRequestItem().getUuid())));


		labManagementService.releaseSamplesForTesting(entity.getUuid(), Arrays.asList(sample1.getUuid()));
		testRequestItems = dao().getTestRequestItemsByTestRequestId(
				Arrays.asList(entity.getId()), false);
		for(TestRequestItem testRequestItem: testRequestItems){
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.REFERRED_OUT_LAB);
		}

		sample1 = dao().getSampleById(sample1.getId());
		Assert.assertEquals(sample1.getStatus(), SampleStatus.TESTING);

	}

	@Test
	public void findWorksheets_shouldSucceedOnAllFilters() {
		Worksheet worksheetDTO = eu().newWorksheet(dao());
		Worksheet entity = labManagementService.saveWorksheet(worksheetDTO);

		Context.flushSession();
		Context.flushSession();

		WorksheetSearchFilter filter = new WorksheetSearchFilter();
		filter.setWorksheetUuid(entity.getUuid());
		Result<WorksheetDTO> result = labManagementService.findWorksheets(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);

		filter = new WorksheetSearchFilter();
		filter.setWorksheetId(entity.getId());
		result = labManagementService.findWorksheets(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);


		filter = new WorksheetSearchFilter();
		filter.setMinActivatedDate(DateUtil.today());
		result = labManagementService.findWorksheets(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new WorksheetSearchFilter();
		filter.setMaxActivatedDate(org.openmrs.util.OpenmrsUtil.getLastMomentOfDay(DateUtil.today()));
		result = labManagementService.findWorksheets(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new WorksheetSearchFilter();
		filter.setMinActivatedDate(DateUtils.addDays(DateUtil.today(), 1));
		result = labManagementService.findWorksheets(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new WorksheetSearchFilter();
		filter.setMaxActivatedDate(DateUtils.addDays(DateUtil.today(), -1));
		result = labManagementService.findWorksheets(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new WorksheetSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findWorksheets(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new WorksheetSearchFilter();
		filter.setIncludeWorksheetItems(true);
		filter.setLimit(10);
		result = labManagementService.findWorksheets(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new WorksheetSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findWorksheets(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		filter = new WorksheetSearchFilter();
		filter.setIncludeWorksheetItemConcept(true);
		filter.setIncludeWorksheetItemTestResult(true);
		filter.setLimit(10);
		result = labManagementService.findWorksheets(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new WorksheetSearchFilter();
		filter.setTestConceptIds(Arrays.asList(1000));
		filter.setIncludeWorksheetItemTestResult(true);
		filter.setLimit(10);
		result = labManagementService.findWorksheets(filter);
		assertTrue(result.getData().isEmpty());


		filter = new WorksheetSearchFilter();
		filter.setSampleRef("BH9000");
		filter.setIncludeWorksheetItemTestResult(true);
		filter.setLimit(10);
		result = labManagementService.findWorksheets(filter);
		assertTrue(result.getData().isEmpty());
	}

	@Test
	public void findWorksheetItems_shouldSucceedOnAllFilters() {
		WorksheetItem worksheetItem = eu().newWorksheetItem(dao());
		WorksheetItem entity = labManagementService.saveWorksheetItem(worksheetItem);

		Context.flushSession();
		Context.flushSession();

		WorksheetItemSearchFilter filter = new WorksheetItemSearchFilter();
		filter.setWorksheetItemUuid(entity.getUuid());
		Result<WorksheetItemDTO> result = labManagementService.findWorksheetItems(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);

		filter = new WorksheetItemSearchFilter();
		filter.setWorksheetItemId(entity.getId());
		result = labManagementService.findWorksheetItems(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);

		filter = new WorksheetItemSearchFilter();
		filter.setWorksheetIds(Arrays.asList(entity.getWorksheet().getId()));
		result = labManagementService.findWorksheetItems(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);

		filter = new WorksheetItemSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findWorksheetItems(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new WorksheetItemSearchFilter();
		filter.setLimit(10);
		filter.setIncludeTestResultId(true);
		result = labManagementService.findWorksheetItems(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new WorksheetItemSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findWorksheetItems(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		filter = new WorksheetItemSearchFilter();
		filter.setIncludeTestConcept(true);
		filter.setIncludeTestResult(true);
		result = labManagementService.findWorksheetItems(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid())));
		assertEquals(result.getData().size(), 1);

	}

	@Test
	public void shouldDeleteWorksheetItems() {
		WorksheetItem worksheetItem = eu().newWorksheetItem(dao());
		WorksheetItem entity = labManagementService.saveWorksheetItem(worksheetItem);

		Context.flushSession();
		Context.flushSession();

		labManagementService.deleteWorksheetItem(worksheetItem.getId(), "Testing");
		Worksheet worksheet = labManagementService.getWorksheetById(worksheetItem.getWorksheet().getId());
		Assert.assertEquals(worksheet.getStatus(), WorksheetStatus.CANCELLED);
	}

	@Test
	public void shouldDeleteWorksheet() {
		WorksheetItem worksheetItem = eu().newWorksheetItem(dao());
		WorksheetItem entity = labManagementService.saveWorksheetItem(worksheetItem);

		Context.flushSession();
		Context.flushSession();

		Worksheet worksheet = labManagementService.getWorksheetById(entity.getWorksheet().getId());
		labManagementService.deleteWorksheet(worksheet.getId(), "Testing");
	}

	@Test
	public void findSamplesForWorksheet_shouldSucceedOnAllFilters() {
		SetGlobalProperties();
		TestRequestDTO testRequestDTO = eu().newLabRequestReferral(dao());
		TestRequest testRequest = labManagementService.saveTestRequest(testRequestDTO);

		Context.flushSession();
		Context.flushSession();
		Context.updateSearchIndexForType(Sample.class);



		SampleSearchFilter filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setForWorksheet(true);
		filter.setTestRequestId(testRequest.getId());
		Result<SampleDTO> result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().allMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		assertEquals(result.getData().size(), (int)testRequestDTO.getSamples().stream().map(p-> p.getTests().size()).reduce(0,(x,y)-> x+y) );
		List<SampleDTO> sampleIds = result.getData();


		int sampleIdsSize = sampleIds.size();
		for(SampleDTO sample : sampleIds) {
			sampleIdsSize = sampleIdsSize - 1;
			// Verify all the tests for the sample match
			TestRequestSampleDTO draftSample = testRequestDTO.getSamples().stream()
					.filter(p -> p.getAccessionNumber().equals(sample.getAccessionNumber()))
					.findFirst().orElse(null);
			Assert.assertNotNull(draftSample);
			Assert.assertTrue(sample.getTests().
					stream().
					allMatch(p -> draftSample.getTests().stream().anyMatch(x -> x.getTestUuid().equals(p.getTestUuid()))));

			TestRequestAction testRequestAction = new TestRequestAction();
			testRequestAction.setAction(ApprovalResult.APPROVED);
			testRequestAction.setRemarks("Approved");
			testRequestAction.setRecords(sample.getTests().stream().map(p -> p.getUuid()).collect(Collectors.toList()));
			ApprovalDTO approvalDTO = labManagementService.approveTestRequestItem(testRequestAction);

			TestRequest entity = labManagementService.getTestRequestById(testRequest.getId());
			if (sampleIdsSize == 0) {
				Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);
			}
			int matched = 0;
			for (TestRequestItem testRequestItem : dao().getTestRequestItemsByTestRequestId(
					Collections.singletonList(entity.getId()), false)) {
				if (sample.getTests().stream().anyMatch(p -> p.getUuid().equals(testRequestItem.getUuid()))) {
					Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.IN_PROGRESS);
					matched++;
				}
			}
			Assert.assertTrue(matched > 0);

			SampleSearchFilter sampleSearchFilter = new SampleSearchFilter();
			sampleSearchFilter.setForWorksheet(false);
			sampleSearchFilter.setSampleStatuses(Arrays.asList(SampleStatus.TESTING));
			sampleSearchFilter.setIncludeTests(true);
			SampleDTO thisSample = labManagementService.findSamples(sampleSearchFilter).getData()
					.stream()
					.filter(p -> p.getUuid().equals(sample.getUuid())).findFirst().orElse(null);
			Assert.assertNotNull(thisSample);


			sampleSearchFilter = new SampleSearchFilter();
			sampleSearchFilter.setForWorksheet(true);
			sampleSearchFilter.setSampleStatuses(Arrays.asList(SampleStatus.TESTING));
			sampleSearchFilter.setIncludeTests(true);
			Assert.assertNotNull(labManagementService.findSamples(sampleSearchFilter).getData()
					.stream()
					.filter(p -> p.getTestRequestItemSampleUuid().equals(sample.getTestRequestItemSampleUuid())).findFirst().orElse(null));

			WorksheetDTO worksheetDTO = new WorksheetDTO();
			worksheetDTO.setResponsiblePersonUuid(eu().getUser().getUuid());
			worksheetDTO.setWorksheetDate(new Date());
			worksheetDTO.setAtLocationUuid(eu().getLocation().getUuid());
			worksheetDTO.setRemarks("Testing");
			worksheetDTO.setWorksheetItems(new ArrayList<>());

				WorksheetItemDTO worksheetItemDTO = new WorksheetItemDTO();
				worksheetItemDTO.setTestRequestItemSampleUuid(sample.getTestRequestItemSampleUuid());
				worksheetDTO.getWorksheetItems().add(worksheetItemDTO);


			Worksheet worksheet1 = labManagementService.saveWorksheet(worksheetDTO);

			sampleSearchFilter = new SampleSearchFilter();
			sampleSearchFilter.setForWorksheet(false);
			sampleSearchFilter.setSampleStatuses(Arrays.asList(SampleStatus.TESTING));
			sampleSearchFilter.setIncludeTests(true);
			SampleDTO thisSample1 = labManagementService.findSamples(sampleSearchFilter).getData()
					.stream()
					.filter(p -> p.getUuid().equals(sample.getUuid())).findFirst().orElse(null);
			Assert.assertNotNull(thisSample1);

			sampleSearchFilter = new SampleSearchFilter();
			sampleSearchFilter.setForWorksheet(true);
			sampleSearchFilter.setSampleStatuses(Arrays.asList(SampleStatus.TESTING));
			sampleSearchFilter.setIncludeTests(true);
			thisSample1 = labManagementService.findSamples(sampleSearchFilter).getData()
					.stream()
					.filter(p -> p.getTestRequestItemSampleUuid().equals(sample.getTestRequestItemSampleUuid())).findFirst().orElse(null);

			Assert.assertNull(thisSample1);

			// Cancel group1, we still need to find it in test result
			List<WorksheetItem> worksheetItems = dao().getWorksheetItemsByWorksheetId(worksheet1.getId());
			Assert.assertEquals(worksheetItems.size(), 1);
			for (WorksheetItem worksheetItem : worksheetItems) {
				worksheetItem.setStatus(WorksheetItemStatus.CANCELLED);
				dao().saveWorksheetItem(worksheetItem);
			}
			worksheet1 = dao().getWorksheetById(worksheet1.getId());
			labManagementService.checkWorksheetCompletion(worksheet1);
			worksheet1 = dao().getWorksheetById(worksheet1.getId());
			Assert.assertEquals(worksheet1.getStatus(), WorksheetStatus.CANCELLED);


		}
	}

	@Test
	public void findTestResults_shouldSucceedOnAllFilters() {
		TestResult entity = eu().newTestResult(dao());
		entity = dao().saveTestResult(entity);

		Context.flushSession();
		Context.flushSession();

		TestResultSearchFilter filter = new TestResultSearchFilter();
		filter.setTestResultUuid(entity.getUuid());
		Result<TestResultDTO> result = labManagementService.findTestResults(filter);
		TestResult finalEntity = entity;
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(finalEntity.getUuid())));
		assertEquals(result.getData().size(), 1);

		filter = new TestResultSearchFilter();
		filter.setTestResultId(entity.getId());
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity1 = entity;
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(finalEntity1.getUuid())));
		assertEquals(result.getData().size(), 1);


		filter = new TestResultSearchFilter();
		filter.setTestRequestItemIds(Arrays.asList(entity.getTestRequestItemSample().getTestRequestItem().getId()));
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity2 = entity;
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity2.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new TestResultSearchFilter();
		filter.setWorksheetItemIds(Arrays.asList(entity.getWorksheetItem().getId()));
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity3 = entity;
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity3.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new TestResultSearchFilter();
		filter.setRequireApproval(entity.getRequireApproval());
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity4 = entity;
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity4.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new TestResultSearchFilter();
		filter.setRequireApproval(!entity.getRequireApproval());
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity11 = entity;
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity11.getId())));
		assertEquals(result.getData().size(), 0);


		filter = new TestResultSearchFilter();
		filter.setCompleted(entity.getCompleted());
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity5 = entity;
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity5.getId())));
		assertEquals(result.getData().size(), 1);


		filter = new TestResultSearchFilter();
		filter.setCompletedResult(entity.getCompletedResult());
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity6 = entity;
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity6.getId())));
		assertEquals(result.getData().size(), 1);


		filter = new TestResultSearchFilter();
		filter.setRequireApproval(!entity.getRequireApproval());
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity7 = entity;
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity7.getId())));
		assertEquals(result.getData().size(), 0);


		filter = new TestResultSearchFilter();
		filter.setCompleted(!entity.getCompleted());
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity10 = entity;
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity10.getId())));
		assertEquals(result.getData().size(), 0);


		filter = new TestResultSearchFilter();
		filter.setCompletedResult(!entity.getCompletedResult());
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity9 = entity;
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity9.getId())));
		assertEquals(result.getData().size(), 0);


		filter = new TestResultSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity8 = entity;
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(finalEntity8.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new TestResultSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findTestResults(filter);
		TestResult finalEntity12 = entity;
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(finalEntity12.getUuid()) && finalEntity12.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());
	}


	@Test
	public void saveTestResultForWorksheet_shouldSucceed() {
		SetGlobalProperties();
		TestRequestDTO testRequestDTO = eu().newLabRequestReferral(dao());
		TestRequest testRequest = labManagementService.saveTestRequest(testRequestDTO);

		Context.flushSession();
		Context.flushSession();
		Context.updateSearchIndexForType(Sample.class);

		SampleSearchFilter filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setForWorksheet(true);
		filter.setTestRequestId(testRequest.getId());
		Result<SampleDTO> result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().allMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		assertEquals(result.getData().size(), (int)testRequestDTO.getSamples().stream().map(p-> p.getTests().size()).reduce(0,(x,y)-> x+y) );
		List<SampleDTO> sampleIds = result.getData();

		TestRequestAction testRequestAction = new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Approved");
		testRequestAction.setRecords(sampleIds.stream().flatMap(p-> p.getTests().stream()).map(p -> p.getUuid()).collect(Collectors.toList()));
		labManagementService.approveTestRequestItem(testRequestAction);

		TestRequest entity = labManagementService.getTestRequestById(testRequest.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);

		for (TestRequestItem testRequestItem : dao().getTestRequestItemsByTestRequestId(
				Collections.singletonList(entity.getId()), false)) {
				Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.IN_PROGRESS);
		}

		SampleSearchFilter sampleSearchFilter = new SampleSearchFilter();
		sampleSearchFilter.setForWorksheet(false);
		sampleSearchFilter.setSampleStatuses(Arrays.asList(SampleStatus.TESTING));
		sampleSearchFilter.setIncludeTests(true);
		SampleDTO thisSample = labManagementService.findSamples(sampleSearchFilter).getData()
				.stream()
				.filter(p -> p.getUuid().equals(sampleIds.get(0).getUuid())).findFirst().orElse(null);
		Assert.assertNotNull(thisSample);

		WorksheetDTO worksheetDTO = new WorksheetDTO();
		worksheetDTO.setResponsiblePersonUuid(eu().getUser().getUuid());
		worksheetDTO.setWorksheetDate(new Date());
		worksheetDTO.setAtLocationUuid(eu().getLocation().getUuid());
		worksheetDTO.setRemarks("Testing");
		worksheetDTO.setWorksheetItems(new ArrayList<>());

		for(SampleDTO sample : sampleIds) {
			WorksheetItemDTO worksheetItemDTO = new WorksheetItemDTO();
			worksheetItemDTO.setTestRequestItemSampleUuid(sample.getTestRequestItemSampleUuid());
			worksheetDTO.getWorksheetItems().add(worksheetItemDTO);
		}
		Worksheet worksheet = labManagementService.saveWorksheet(worksheetDTO);
		WorksheetTestResultDTO worksheetTestResultDTO=new WorksheetTestResultDTO();
		worksheetTestResultDTO.setWorksheetUuid(worksheet.getUuid());
		worksheetTestResultDTO.setTestResults(new ArrayList<>());
		for(WorksheetItem worksheetItem : dao().getWorksheetItemsByWorksheetId(worksheet.getId())){
			TestResultDTO testResultDTO = new TestResultDTO();
			testResultDTO.setTestRequestItemSampleUuid(worksheetItem.getTestRequestItemSample().getUuid());
			testResultDTO.setWorksheetItemUuid(worksheetItem.getUuid());
			testResultDTO.setRemarks(eu().getRandomString(300));
			testResultDTO.setAdditionalTestsRequired(false);
			testResultDTO.setArchiveSample(false);
			Obs obs = new Obs();
			obs.setConcept(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder().getConcept());
			obs.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
			obs.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

			if(obs.getConcept().getDatatype().isCoded()){
				obs.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
			}else if(obs.getConcept().getDatatype().isNumeric()){
				obs.setValueNumeric(eu().getRandomDouble());
			}else{
				obs.setValueText("Result");
			}

			if(obs.getConcept().getSetMembers() != null && obs.getConcept().getSetMembers().size() > 0){
				obs.setGroupMembers(new HashSet<>());
				int index = 0;
				for(Concept concept : obs.getConcept().getSetMembers()){
					Obs obsChild = new Obs();
					obsChild.setConcept(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder().getConcept());
					obsChild.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
					obsChild.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

					if(obsChild.getConcept().getDatatype().isCoded()){
						obsChild.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
					}else if(obsChild.getConcept().getDatatype().isNumeric()){
						obsChild.setValueNumeric(eu().getRandomDouble());
					}else{
						obsChild.setValueText("Result" + Integer.toString(++index));
					}
					obs.getGroupMembers().add(obsChild);
				}
			}

			testResultDTO.setObs(obs);
			testResultDTO.setAtLocationUuid(eu().getLocation().getUuid());
			worksheetTestResultDTO.getTestResults().add(testResultDTO);
		}
		labManagementService.saveWorksheetTestResults(worksheetTestResultDTO);


		TestRequestSearchFilter testRequestSearchFilter = new TestRequestSearchFilter();
		testRequestSearchFilter.setOnlyPendingResultApproval(true);
		testRequestSearchFilter.setPendingResultApproval(true);
		testRequestSearchFilter.setIncludeTestItems(true);
		testRequestSearchFilter.setIncludeTestItemTestResult(true);
		testRequestSearchFilter.setPermApproval(true);
		Result<TestRequestDTO> testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
		assertTrue(result.getData().size() > 1);

//		for(WorksheetItem worksheetItem : dao().getWorksheetItemsByWorksheetId(worksheet.getId())){
//			List<TestResult> testResults = dao().getTestResultsByWorksheetItem(worksheetItem);
//			for(TestResult testResult : testResults){
//				Assert.assertFalse(testResult.getCompleted());
//			}
//		}

		testRequestSearchFilter = new TestRequestSearchFilter();
		testRequestSearchFilter.setIncludeTestItems(true);
		testRequestSearchFilter.setIncludeTestItemTestResult(true);
		testRequestSearchFilter.setIncludeTestItemTestResultApprovals(true);
		Result<TestRequestDTO> testRequestSearchresult = labManagementService.findTestRequests(testRequestSearchFilter);
		assertTrue(!testRequestSearchresult.getData().isEmpty());
		assertTrue(testRequestSearchresult.getData().get(0).getTests().stream().anyMatch(p->p.getTestResult() != null &&
				p.getTestResult().getApprovals() != null &&
				!p.getTestResult().getApprovals().isEmpty()));

	}


	@Test
	public void obsModification_shouldSucceed() {
		SetGlobalProperties();
		TestRequestDTO testRequestDTO = eu().newLabRequestReferral(dao());
		TestRequest testRequest = labManagementService.saveTestRequest(testRequestDTO);

		Context.flushSession();
		Context.flushSession();
		Context.updateSearchIndexForType(Sample.class);

		SampleSearchFilter filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setForWorksheet(true);
		filter.setTestRequestId(testRequest.getId());
		Result<SampleDTO> result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().allMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		assertEquals(result.getData().size(), (int)testRequestDTO.getSamples().stream().map(p-> p.getTests().size()).reduce(0,(x,y)-> x+y) );
		List<SampleDTO> sampleIds = result.getData();

		TestRequestAction testRequestAction = new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Approved");
		testRequestAction.setRecords(sampleIds.stream().flatMap(p-> p.getTests().stream()).map(p -> p.getUuid()).collect(Collectors.toList()));
		labManagementService.approveTestRequestItem(testRequestAction);

		TestRequest entity = labManagementService.getTestRequestById(testRequest.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);

		for (TestRequestItem testRequestItem : dao().getTestRequestItemsByTestRequestId(
				Collections.singletonList(entity.getId()), false)) {
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.IN_PROGRESS);
		}

		SampleSearchFilter sampleSearchFilter = new SampleSearchFilter();
		sampleSearchFilter.setForWorksheet(false);
		sampleSearchFilter.setSampleStatuses(Arrays.asList(SampleStatus.TESTING));
		sampleSearchFilter.setIncludeTests(true);
		SampleDTO thisSample = labManagementService.findSamples(sampleSearchFilter).getData()
				.stream()
				.filter(p -> p.getUuid().equals(sampleIds.get(0).getUuid())).findFirst().orElse(null);
		Assert.assertNotNull(thisSample);

		WorksheetDTO worksheetDTO = new WorksheetDTO();
		worksheetDTO.setResponsiblePersonUuid(eu().getUser().getUuid());
		worksheetDTO.setWorksheetDate(new Date());
		worksheetDTO.setAtLocationUuid(eu().getLocation().getUuid());
		worksheetDTO.setRemarks("Testing");
		worksheetDTO.setWorksheetItems(new ArrayList<>());

		for(SampleDTO sample : sampleIds) {
			WorksheetItemDTO worksheetItemDTO = new WorksheetItemDTO();
			worksheetItemDTO.setTestRequestItemSampleUuid(sample.getTestRequestItemSampleUuid());
			worksheetDTO.getWorksheetItems().add(worksheetItemDTO);
		}
		Worksheet worksheet = labManagementService.saveWorksheet(worksheetDTO);
		WorksheetTestResultDTO worksheetTestResultDTO=new WorksheetTestResultDTO();
		worksheetTestResultDTO.setWorksheetUuid(worksheet.getUuid());
		worksheetTestResultDTO.setTestResults(new ArrayList<>());
		for(WorksheetItem worksheetItem : dao().getWorksheetItemsByWorksheetId(worksheet.getId())){
			TestResultDTO testResultDTO = new TestResultDTO();
			testResultDTO.setTestRequestItemSampleUuid(worksheetItem.getTestRequestItemSample().getUuid());
			testResultDTO.setWorksheetItemUuid(worksheetItem.getUuid());
			testResultDTO.setRemarks(eu().getRandomString(300));
			testResultDTO.setAdditionalTestsRequired(false);
			testResultDTO.setArchiveSample(false);
			Obs obs = new Obs();
			obs.setConcept(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder().getConcept());
			obs.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
			obs.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

			if(obs.getConcept().getDatatype().isCoded()){
				obs.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
			}else if(obs.getConcept().getDatatype().isNumeric()){
				obs.setValueNumeric(eu().getRandomDouble());
			}else{
				obs.setValueText("Result");
			}

			if(obs.getConcept().getSetMembers() != null && !obs.getConcept().getSetMembers().isEmpty()){
				obs.setValueText(null);
				int index = 0;
				for(Concept concept : obs.getConcept().getSetMembers()){
					Obs obsChild = new Obs();
					obsChild.setConcept(concept);
					obsChild.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
					obsChild.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

					if(obsChild.getConcept().getDatatype().isCoded()){
						obsChild.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
					}else if(obsChild.getConcept().getDatatype().isNumeric()){
						obsChild.setValueNumeric(eu().getRandomDouble());
					}else if(obsChild.getConcept().getDatatype().isBoolean()){
						obsChild.setValueBoolean(false);
					} else if(obsChild.getConcept().getDatatype().isDate()){
						obsChild.setValueDate(new Date());
					}
					else if(obsChild.getConcept().getDatatype().isDateTime() ||
							obsChild.getConcept().getDatatype().isTime()){
						obsChild.setValueDatetime(new Date());
					}
					else if(obsChild.getConcept().getDatatype().isTime()){
						obsChild.setValueTime(new Date());
					}else{
						obsChild.setValueText("Result" + Integer.toString(++index));
					}
					obs.addGroupMember(obsChild);
				}
			}

			testResultDTO.setObs(obs);
			testResultDTO.setAtLocationUuid(eu().getLocation().getUuid());
			worksheetTestResultDTO.getTestResults().add(testResultDTO);
		}
		labManagementService.saveWorksheetTestResults(worksheetTestResultDTO);


		TestRequestSearchFilter testRequestSearchFilter = new TestRequestSearchFilter();
		testRequestSearchFilter.setOnlyPendingResultApproval(true);
		testRequestSearchFilter.setPendingResultApproval(true);
		testRequestSearchFilter.setIncludeTestItems(true);
		testRequestSearchFilter.setIncludeTestItemTestResult(true);
		testRequestSearchFilter.setPermApproval(true);
		Result<TestRequestDTO> testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
		assertTrue(result.getData().size() > 1);

		checkEncounterObsIsAsExpected(worksheet);

		Context.flushSession();
		Context.flushSession();

		// Check we can overwrite the values first time
		worksheetTestResultDTO=new WorksheetTestResultDTO();
		worksheetTestResultDTO.setWorksheetUuid(worksheet.getUuid());
		worksheetTestResultDTO.setTestResults(new ArrayList<>());
		for(WorksheetItem worksheetItem : dao().getWorksheetItemsByWorksheetId(worksheet.getId())){
			TestResultDTO testResultDTO = new TestResultDTO();
			testResultDTO.setTestRequestItemSampleUuid(worksheetItem.getTestRequestItemSample().getUuid());
			testResultDTO.setWorksheetItemUuid(worksheetItem.getUuid());
			testResultDTO.setRemarks(eu().getRandomString(300));
			testResultDTO.setAdditionalTestsRequired(false);
			testResultDTO.setArchiveSample(false);
			Obs obs = new Obs();
			obs.setConcept(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder().getConcept());
			obs.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
			obs.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

			if(obs.getConcept().getDatatype().isCoded()){
				obs.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
			}else if(obs.getConcept().getDatatype().isNumeric()){
				obs.setValueNumeric(eu().getRandomDouble());
			}else{
				obs.setValueText("Result2.");
			}

			if(obs.getConcept().getSetMembers() != null && obs.getConcept().getSetMembers().size() > 0){
				obs.setValueText(null);
				int index = 0;
				for(Concept concept : obs.getConcept().getSetMembers()){
					Obs obsChild = new Obs();
					obsChild.setConcept(concept);
					obsChild.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
					obsChild.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

					if(obsChild.getConcept().getDatatype().isCoded()){
						obsChild.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
					}else if(obsChild.getConcept().getDatatype().isNumeric()){
						obsChild.setValueNumeric(eu().getRandomDouble());
					}else if(obsChild.getConcept().getDatatype().isBoolean()){
						obsChild.setValueBoolean(false);
					} else if(obsChild.getConcept().getDatatype().isDate()){
						obsChild.setValueDate(new Date());
					}
					else if(obsChild.getConcept().getDatatype().isDateTime() ||
							obsChild.getConcept().getDatatype().isTime()){
						obsChild.setValueDatetime(new Date());
					}
					else if(obsChild.getConcept().getDatatype().isTime()){
						obsChild.setValueTime(new Date());
					}else{
						obsChild.setValueText("Result2-" + Integer.toString(++index));
					}
					obs.addGroupMember(obsChild);
				}
			}

			testResultDTO.setObs(obs);
			testResultDTO.setAtLocationUuid(eu().getLocation().getUuid());
			worksheetTestResultDTO.getTestResults().add(testResultDTO);
		}
		labManagementService.saveWorksheetTestResults(worksheetTestResultDTO);

		Context.flushSession();
		Context.flushSession();

		checkEncounterObsIsAsExpected(worksheet);

		// Check we can overwrite the values second time
		worksheetTestResultDTO=new WorksheetTestResultDTO();
		worksheetTestResultDTO.setWorksheetUuid(worksheet.getUuid());
		worksheetTestResultDTO.setTestResults(new ArrayList<>());
		for(WorksheetItem worksheetItem : dao().getWorksheetItemsByWorksheetId(worksheet.getId())){
			TestResultDTO testResultDTO = new TestResultDTO();
			testResultDTO.setTestRequestItemSampleUuid(worksheetItem.getTestRequestItemSample().getUuid());
			testResultDTO.setWorksheetItemUuid(worksheetItem.getUuid());
			testResultDTO.setRemarks(eu().getRandomString(300));
			testResultDTO.setAdditionalTestsRequired(false);
			testResultDTO.setArchiveSample(false);
			Obs obs = new Obs();
			obs.setConcept(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder().getConcept());
			obs.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
			obs.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

			if(obs.getConcept().getDatatype().isCoded()){
				obs.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
			}else if(obs.getConcept().getDatatype().isNumeric()){
				obs.setValueNumeric(eu().getRandomDouble());
			}else{
				obs.setValueText("Result2x1.");
			}

			if(obs.getConcept().getSetMembers() != null && obs.getConcept().getSetMembers().size() > 0){
				obs.setValueText(null);
				int index = 0;
				for(Concept concept : obs.getConcept().getSetMembers()){
					Obs obsChild = new Obs();
					obsChild.setConcept(concept);
					obsChild.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
					obsChild.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

					if(obsChild.getConcept().getDatatype().isCoded()){
						obsChild.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
					}else if(obsChild.getConcept().getDatatype().isNumeric()){
						obsChild.setValueNumeric(eu().getRandomDouble());
					}else if(obsChild.getConcept().getDatatype().isBoolean()){
						obsChild.setValueBoolean(false);
					} else if(obsChild.getConcept().getDatatype().isDate()){
						obsChild.setValueDate(new Date());
					}
					else if(obsChild.getConcept().getDatatype().isDateTime() ||
							obsChild.getConcept().getDatatype().isTime()){
						obsChild.setValueDatetime(new Date());
					}
					else if(obsChild.getConcept().getDatatype().isTime()){
						obsChild.setValueTime(new Date());
					}else{
						obsChild.setValueText("Result2x1-" + Integer.toString(++index));
					}
					obs.addGroupMember(obsChild);
				}
			}

			testResultDTO.setObs(obs);
			testResultDTO.setAtLocationUuid(eu().getLocation().getUuid());
			worksheetTestResultDTO.getTestResults().add(testResultDTO);
		}
		labManagementService.saveWorksheetTestResults(worksheetTestResultDTO);
		Context.flushSession();
		Context.flushSession();

		checkEncounterObsIsAsExpected(worksheet);

		Context.flushSession();
		Context.flushSession();



		testRequestSearchFilter = new TestRequestSearchFilter();
		testRequestSearchFilter.setIncludeTestItems(true);
		testRequestSearchFilter.setIncludeTestItemTestResult(true);
		testRequestSearchFilter.setRequestItemMatch(RequestItemMatchOptions.NoWorkStarted);
		testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
		assertTrue(testRequests.getData().isEmpty());

		testRequestSearchFilter.setRequestItemMatch(RequestItemMatchOptions.NoResults);
		testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
		assertTrue(testRequests.getData().isEmpty());

		testRequestSearchFilter.setRequestItemMatch(RequestItemMatchOptions.Results);
		testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
        assertFalse(testRequests.getData().isEmpty());

		testRequestSearchFilter.setRequestItemMatch(RequestItemMatchOptions.Rejected);
		testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
		assertTrue(testRequests.getData().isEmpty());

		testRequestSearchFilter.setRequestItemMatch(RequestItemMatchOptions.Worksheet);
		testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
        assertFalse(testRequests.getData().isEmpty());

		testRequestSearchFilter.setRequestItemMatch(RequestItemMatchOptions.WorksheetNoResults);
		testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
		assertTrue(testRequests.getData().isEmpty());

		testRequestSearchFilter.setRequestItemMatch(RequestItemMatchOptions.WorksheetResults);
		testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
        assertFalse(testRequests.getData().isEmpty());

		testRequestSearchFilter.setRequestItemMatch(RequestItemMatchOptions.NoWorksheetResults);
		testRequests = labManagementService.findTestRequests(testRequestSearchFilter);
		assertTrue(testRequests.getData().isEmpty());


		// Check we can overwrite the values third time with same obs id
		worksheetTestResultDTO=new WorksheetTestResultDTO();
		worksheetTestResultDTO.setWorksheetUuid(worksheet.getUuid());
		worksheetTestResultDTO.setTestResults(new ArrayList<>());
		Map<Integer, TestResult> oldTestResults = new HashMap<>();
		for(WorksheetItem worksheetItem : dao().getWorksheetItemsByWorksheetId(worksheet.getId())) {
			TestResult testResult = dao().getTestResultsByWorksheetItem(worksheetItem).get(0);
			Obs obs = testResult.getObs();
			oldTestResults.putIfAbsent(worksheetItem.getId(), testResult);
		}
		for(WorksheetItem worksheetItem : dao().getWorksheetItemsByWorksheetId(worksheet.getId())){
			TestResultDTO testResultDTO = new TestResultDTO();
			testResultDTO.setTestRequestItemSampleUuid(worksheetItem.getTestRequestItemSample().getUuid());
			testResultDTO.setWorksheetItemUuid(worksheetItem.getUuid());
			testResultDTO.setRemarks(eu().getRandomString(300));
			testResultDTO.setAdditionalTestsRequired(false);
			testResultDTO.setArchiveSample(false);
			TestResult testResult = oldTestResults.get(worksheetItem.getId());
			Obs obs = testResult.getObs();
			obs.setConcept(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder().getConcept());
			obs.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
			obs.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

			if(obs.getConcept().getDatatype().isCoded()){
				obs.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
			}else if(obs.getConcept().getDatatype().isNumeric()){
				obs.setValueNumeric(eu().getRandomDouble());
			}else{
				obs.setValueText("Result3.");
			}

			if(obs.getConcept().getSetMembers() != null && obs.getConcept().getSetMembers().size() > 0){
				obs.setValueText(null);
				int index = 0;
				for(Concept concept : obs.getConcept().getSetMembers()){
					Obs obsChild = new Obs();
					obsChild.setConcept(concept);
					obsChild.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
					obsChild.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

					if(obsChild.getConcept().getDatatype().isCoded()){
						obsChild.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
					}else if(obsChild.getConcept().getDatatype().isNumeric()){
						obsChild.setValueNumeric(eu().getRandomDouble());
					} else if(obsChild.getConcept().getDatatype().isBoolean()){
						obsChild.setValueBoolean(false);
					} else if(obsChild.getConcept().getDatatype().isDate()){
						obsChild.setValueDate(new Date());
					}
					else if(obsChild.getConcept().getDatatype().isDateTime() ||
							obsChild.getConcept().getDatatype().isTime()){
						obsChild.setValueDatetime(new Date());
					}
					else if(obsChild.getConcept().getDatatype().isTime()){
						obsChild.setValueTime(new Date());
					}
					else{
						obsChild.setValueText("Result3-" + Integer.toString(++index));
					}
					obs.addGroupMember(obsChild);
				}
			}

			testResultDTO.setObs(obs);
			testResultDTO.setAtLocationUuid(eu().getLocation().getUuid());
			worksheetTestResultDTO.getTestResults().add(testResultDTO);
		}

		WorksheetTestResultDTO finalWorksheetTestResultDTO = worksheetTestResultDTO;
		Assert.assertThrows(Exception.class,()->{
			labManagementService.saveWorksheetTestResults(finalWorksheetTestResultDTO);
			Context.flushSession();
			Context.flushSession();
			checkEncounterObsIsAsExpected(worksheet);
		});
	}

	private void checkEncounterObsIsAsExpected(Worksheet worksheet) {
		List<Obs> obsToVerify = new ArrayList<>();
		for(WorksheetItem worksheetItem : dao().getWorksheetItemsByWorksheetId(worksheet.getId())){
			TestResult testResult = dao().getTestResultsByWorksheetItem(worksheetItem).get(0);
			Obs obs = testResult.getObs();
			obsToVerify.add(obs);
			if(obs.getConcept().getSetMembers() == null || obs.getConcept().getSetMembers().isEmpty()){
				Assert.assertTrue(obs.getGroupMembers() == null || obs.getGroupMembers().isEmpty());
			}else{
				Assert.assertEquals(obs.getGroupMembers().size(), obs.getConcept().getSetMembers().size());
			}
		}

		Encounter encounter = Context.getEncounterService().getEncounter(obsToVerify.get(0).getEncounter().getId());
		Set<Obs> encounterObss = encounter.getObsAtTopLevel(false);
		Assert.assertNotNull(encounterObss);
		Assert.assertEquals(encounterObss.size(), obsToVerify.size());
		for(Obs encounterObs : encounterObss){
			List<Obs> testResultObs = obsToVerify.stream().filter(p->p.getObsId().equals(encounterObs.getObsId())).collect(Collectors.toList());
			Assert.assertEquals(testResultObs.size(), 1);
		}
	}

	@Test
	public void getDashboardMetrics_shouldSucceed() {
		SetGlobalProperties();
		TestRequestDTO testRequestDTO = eu().newLabRequestReferral(dao());
		TestRequest testRequest = labManagementService.saveTestRequest(testRequestDTO);

		Context.flushSession();
		Context.flushSession();
		Context.updateSearchIndexForType(Sample.class);

		DashboardMetricsDTO dashboardMetricsDTO = labManagementService.getDashboardMetrics(DateUtils.addDays(new Date(), -1),
				DateUtil.endOfDay(new Date()));

		Assert.assertEquals(dashboardMetricsDTO.getTestsToAccept(), new Long(10L));

		SampleSearchFilter filter = new SampleSearchFilter();
		filter.setIncludeTests(true);
		filter.setForWorksheet(true);
		filter.setTestRequestId(testRequest.getId());
		Result<SampleDTO> result = labManagementService.findSamples(filter);
		assertTrue(result.getData().stream().allMatch(p -> p.getTestRequestUuid().equalsIgnoreCase(testRequest.getUuid())));
		assertEquals(result.getData().size(), (int)testRequestDTO.getSamples().stream().map(p-> p.getTests().size()).reduce(0,(x,y)-> x+y) );
		List<SampleDTO> sampleIds = result.getData();

		TestRequestAction testRequestAction = new TestRequestAction();
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRemarks("Approved");
		testRequestAction.setRecords(sampleIds.stream().flatMap(p-> p.getTests().stream()).map(p -> p.getUuid()).collect(Collectors.toList()));
		labManagementService.approveTestRequestItem(testRequestAction);

		dashboardMetricsDTO = labManagementService.getDashboardMetrics(DateUtils.addDays(new Date(), -1),
				DateUtil.endOfDay(new Date()));
		Assert.assertEquals(dashboardMetricsDTO.getTestsInProgress(), new Long(10));
		Assert.assertEquals(dashboardMetricsDTO.getTestsOnWorksheet(), new Long(0));

		TestRequest entity = labManagementService.getTestRequestById(testRequest.getId());
		Assert.assertEquals(entity.getStatus(), TestRequestStatus.IN_PROGRESS);

		for (TestRequestItem testRequestItem : dao().getTestRequestItemsByTestRequestId(
				Collections.singletonList(entity.getId()), false)) {
			Assert.assertEquals(testRequestItem.getStatus(), TestRequestItemStatus.IN_PROGRESS);
		}

		SampleSearchFilter sampleSearchFilter = new SampleSearchFilter();
		sampleSearchFilter.setForWorksheet(false);
		sampleSearchFilter.setSampleStatuses(Arrays.asList(SampleStatus.TESTING));
		sampleSearchFilter.setIncludeTests(true);
		SampleDTO thisSample = labManagementService.findSamples(sampleSearchFilter).getData()
				.stream()
				.filter(p -> p.getUuid().equals(sampleIds.get(0).getUuid())).findFirst().orElse(null);
		Assert.assertNotNull(thisSample);

		WorksheetDTO worksheetDTO = new WorksheetDTO();
		worksheetDTO.setResponsiblePersonUuid(eu().getUser().getUuid());
		worksheetDTO.setWorksheetDate(new Date());
		worksheetDTO.setAtLocationUuid(eu().getLocation().getUuid());
		worksheetDTO.setRemarks("Testing");
		worksheetDTO.setWorksheetItems(new ArrayList<>());

		for(SampleDTO sample : sampleIds) {
			WorksheetItemDTO worksheetItemDTO = new WorksheetItemDTO();
			worksheetItemDTO.setTestRequestItemSampleUuid(sample.getTestRequestItemSampleUuid());
			worksheetDTO.getWorksheetItems().add(worksheetItemDTO);
		}
		Worksheet worksheet = labManagementService.saveWorksheet(worksheetDTO);

		TestRequestItemSearchFilter testRequestItemSearchFilter=new TestRequestItemSearchFilter();
		testRequestItemSearchFilter.setSampleId(thisSample.getId());
		testRequestItemSearchFilter.setIncludeTestWorksheetInfo(true);
		Result<TestRequestItemDTO> testRequestItems =  labManagementService.findTestRequestItems(testRequestItemSearchFilter);
		Assert.assertTrue(!testRequestItems.getData().isEmpty());
		Assert.assertEquals(testRequestItems.getData().get(0).getWorksheetUuid(), worksheet.getUuid());


		dashboardMetricsDTO = labManagementService.getDashboardMetrics(DateUtils.addDays(new Date(), -1),
				DateUtil.endOfDay(new Date()));
		Assert.assertEquals(dashboardMetricsDTO.getTestsInProgress(), new Long(10));
		Assert.assertEquals(dashboardMetricsDTO.getTestsOnWorksheet(), new Long(10));

		WorksheetTestResultDTO worksheetTestResultDTO=new WorksheetTestResultDTO();
		worksheetTestResultDTO.setWorksheetUuid(worksheet.getUuid());
		worksheetTestResultDTO.setTestResults(new ArrayList<>());
		for(WorksheetItem worksheetItem : dao().getWorksheetItemsByWorksheetId(worksheet.getId())){
			TestResultDTO testResultDTO = new TestResultDTO();
			testResultDTO.setTestRequestItemSampleUuid(worksheetItem.getTestRequestItemSample().getUuid());
			testResultDTO.setWorksheetItemUuid(worksheetItem.getUuid());
			testResultDTO.setRemarks(eu().getRandomString(300));
			testResultDTO.setAdditionalTestsRequired(false);
			testResultDTO.setArchiveSample(false);
			Obs obs = new Obs();
			obs.setConcept(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder().getConcept());
			obs.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
			obs.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

			if(obs.getConcept().getDatatype().isCoded()){
				obs.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
			}else if(obs.getConcept().getDatatype().isNumeric()){
				obs.setValueNumeric(eu().getRandomDouble());
			}else{
				obs.setValueText("Result");
			}

			if(obs.getConcept().getSetMembers() != null && obs.getConcept().getSetMembers().size() > 0){
				obs.setGroupMembers(new HashSet<>());
				int index = 0;
				for(Concept concept : obs.getConcept().getSetMembers()){
					Obs obsChild = new Obs();
					obsChild.setConcept(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder().getConcept());
					obsChild.setOrder(worksheetItem.getTestRequestItemSample().getTestRequestItem().getOrder());
					obsChild.setEncounter(worksheetItem.getTestRequestItemSample().getTestRequestItem().getEncounter());

					if(obsChild.getConcept().getDatatype().isCoded()){
						obsChild.setValueCoded(obs.getConcept().getAnswers().stream().findFirst().orElse(null).getConcept());
					}else if(obsChild.getConcept().getDatatype().isNumeric()){
						obsChild.setValueNumeric(eu().getRandomDouble());
					}else{
						obsChild.setValueText("Result" + Integer.toString(++index));
					}
					obs.getGroupMembers().add(obsChild);
				}
			}

			testResultDTO.setObs(obs);
			testResultDTO.setAtLocationUuid(eu().getLocation().getUuid());
			worksheetTestResultDTO.getTestResults().add(testResultDTO);
		}
		labManagementService.saveWorksheetTestResults(worksheetTestResultDTO);

		dashboardMetricsDTO = labManagementService.getDashboardMetrics(DateUtils.addDays(new Date(), -1),
				DateUtil.endOfDay(new Date()));
		Assert.assertEquals(dashboardMetricsDTO.getTestsInProgress(), new Long(0));
		Assert.assertEquals(dashboardMetricsDTO.getTestsOnWorksheet(), new Long(10));
		Assert.assertEquals(dashboardMetricsDTO.getTestsPendingApproval(), new Long(10));

		TestRequestReportItemFilter testRequestReportItemFilter=new TestRequestReportItemFilter();
		Result<TestRequestReportItem> reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestAction = new TestRequestAction();
		testRequestAction.setParameters(new LinkedHashMap<>());
		testRequestAction.setAction(ApprovalResult.APPROVED);
		testRequestAction.setRecords(Arrays.asList(sampleIds.get(0).getUuid()));
		testRequestAction.setActionDate(new Date());
		testRequestAction.setRemarks("Test purpose");
		testRequestAction.getParameters().put("responsiblePersonUuid", Context.getAuthenticatedUser().getUuid());
		testRequestAction.getParameters().put("thawCycles", 5);
		testRequestAction.getParameters().put("volume", 100);
		testRequestAction.getParameters().put("volumeUnitUuid", eu().getConcept().getUuid());
		labManagementService.disposeSamples(testRequestAction);

		SampleActivitySearchFilter sampleActivitySearchFilter=new SampleActivitySearchFilter();
		sampleActivitySearchFilter.setSampleId(sampleIds.get(0).getId());
		Result<SampleActivityDTO> sampleActivityDTOResult = labManagementService.findSampleActivities(sampleActivitySearchFilter);
		Assert.assertTrue(sampleActivityDTOResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setPatientId(testRequest.getPatient().getId());
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setDiagonisticLocationId(worksheet.getAtLocation().getId());
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setStartDate(DateUtils.addDays(new Date(), -1));
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setEndDate(DateUtils.addDays(new Date(), 1));
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		TestRequestItem reportItem = dao().getTestRequestItemsByTestRequestId(Collections.singletonList(testRequest.getId()), false)
				.stream()
				.findFirst().orElse(null);
		testRequestReportItemFilter.setTestConceptId(reportItem.getOrder().getConcept().getConceptId());
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setApproverUserId(Context.getAuthenticatedUser().getUserId());
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() == 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setLimit(1);
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setStartIndex(0);
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setTestRequestItemIdMin(reportItem.getId()-1);
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setTestRequestIdMin(testRequest.getId() - 1);
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setPatientId(testRequest.getPatient().getId());
		testRequestReportItemFilter.setDiagonisticLocationId(worksheet.getAtLocation().getId());
		testRequestReportItemFilter.setStartDate(DateUtils.addDays(new Date(), -1));
		testRequestReportItemFilter.setEndDate(DateUtils.addDays(new Date(), 1));
		reportItem = dao().getTestRequestItemsByTestRequestId(Collections.singletonList(testRequest.getId()), false)
				.stream()
				.findFirst().orElse(null);
		testRequestReportItemFilter.setTestConceptId(reportItem.getOrder().getConcept().getConceptId());
		testRequestReportItemFilter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		testRequestReportItemFilter.setLimit(1);
		testRequestReportItemFilter.setStartIndex(0);
		testRequestReportItemFilter.setTestRequestItemIdMin(reportItem.getId()-1);
		testRequestReportItemFilter.setTestRequestIdMin(testRequest.getId()-1);
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter.setTestRequestItemIdMin(100000000);
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() == 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		reportResult = labManagementService.findTestRequestReportItems(testRequestReportItemFilter);

		Map<Integer, List<ObsDto>> obsData = labManagementService.getObservations( reportResult.getData()
				.stream().map(TestRequestReportItem::getOrderId).distinct().collect(Collectors.toList()));
		Assert.assertTrue(obsData.size() > 0);


		List<SummarizedTestReportItem> summary = labManagementService.getSummarizedTestReport(DateUtils.addDays(new Date(), -1), DateUtils.addDays(new Date(), 1), null);
		Assert.assertTrue(summary.size() > 0);

		summary = labManagementService.getSummarizedTestReport(DateUtils.addDays(new Date(), -1), DateUtils.addDays(new Date(), 1), 1000000);
		Assert.assertTrue(summary.isEmpty());

		summary = labManagementService.getIndividualPerformanceReport(DateUtils.addDays(new Date(), -1), DateUtils.addDays(new Date(), 1), null, null);
		Assert.assertTrue(summary.size() > 0);

		summary = labManagementService.getIndividualPerformanceReport(DateUtils.addDays(new Date(), -1), DateUtils.addDays(new Date(), 1), null, Context.getAuthenticatedUser().getUserId());
		Assert.assertTrue(!summary.isEmpty());

		summary = labManagementService.getIndividualPerformanceReport(DateUtils.addDays(new Date(), -1), DateUtils.addDays(new Date(), 1), 1000, Context.getAuthenticatedUser().getUserId());
		Assert.assertTrue(summary.isEmpty());


		// Turn around time

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);


		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setPatientId(testRequest.getPatient().getId());
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setDiagonisticLocationId(worksheet.getAtLocation().getId());
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setStartDate(DateUtils.addDays(new Date(), -1));
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setEndDate(DateUtils.addDays(new Date(), 1));
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportItem = dao().getTestRequestItemsByTestRequestId(Collections.singletonList(testRequest.getId()), false)
				.stream()
				.findFirst().orElse(null);
		testRequestReportItemFilter.setTestConceptId(reportItem.getOrder().getConcept().getConceptId());
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);


		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setLimit(1);
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setStartIndex(0);
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setTestRequestItemIdMin(reportItem.getId()-1);
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setTestRequestIdMin(testRequest.getId() - 1);
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setPatientId(testRequest.getPatient().getId());
		testRequestReportItemFilter.setDiagonisticLocationId(worksheet.getAtLocation().getId());
		testRequestReportItemFilter.setStartDate(DateUtils.addDays(new Date(), -1));
		testRequestReportItemFilter.setEndDate(DateUtils.addDays(new Date(), 1));
		reportItem = dao().getTestRequestItemsByTestRequestId(Collections.singletonList(testRequest.getId()), false)
				.stream()
				.findFirst().orElse(null);
		testRequestReportItemFilter.setTestConceptId(reportItem.getOrder().getConcept().getConceptId());
		testRequestReportItemFilter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		testRequestReportItemFilter.setLimit(1);
		testRequestReportItemFilter.setStartIndex(0);
		testRequestReportItemFilter.setTestRequestItemIdMin(reportItem.getId()-1);
		testRequestReportItemFilter.setTestRequestIdMin(testRequest.getId()-1);
		reportResult = labManagementService.findTurnAroundTestRequestReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);


		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);


		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setPatientId(testRequest.getPatient().getId());
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setDiagonisticLocationId(worksheet.getAtLocation().getId());
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setStartDate(DateUtils.addDays(new Date(), -1));
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setEndDate(DateUtils.addDays(new Date(), 1));
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportItem = dao().getTestRequestItemsByTestRequestId(Collections.singletonList(testRequest.getId()), false)
				.stream()
				.findFirst().orElse(null);
		testRequestReportItemFilter.setTestConceptId(reportItem.getOrder().getConcept().getConceptId());
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setApproverUserId(Context.getAuthenticatedUser().getUserId());
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() == 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setLimit(1);
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setStartIndex(0);
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setTestRequestItemIdMin(reportItem.getId()-1);
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setTestRequestIdMin(testRequest.getId() - 1);
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setPatientId(testRequest.getPatient().getId());
		testRequestReportItemFilter.setDiagonisticLocationId(worksheet.getAtLocation().getId());
		testRequestReportItemFilter.setStartDate(DateUtils.addDays(new Date(), -1));
		testRequestReportItemFilter.setEndDate(DateUtils.addDays(new Date(), 1));
		reportItem = dao().getTestRequestItemsByTestRequestId(Collections.singletonList(testRequest.getId()), false)
				.stream()
				.findFirst().orElse(null);
		testRequestReportItemFilter.setTestConceptId(reportItem.getOrder().getConcept().getConceptId());
		testRequestReportItemFilter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		testRequestReportItemFilter.setLimit(1);
		testRequestReportItemFilter.setStartIndex(0);
		testRequestReportItemFilter.setTestRequestItemIdMin(reportItem.getId()-1);
		testRequestReportItemFilter.setTestRequestIdMin(testRequest.getId()-1);
		reportResult = labManagementService.findAuditReportReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);


		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		labManagementService.getSampleActivitiesForReport(reportResult.getData().stream().map(p->p.getSampleId()).distinct().collect(Collectors.toList()));


		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setPatientId(testRequest.getPatient().getId());
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setDiagonisticLocationId(worksheet.getAtLocation().getId());
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setStartDate(DateUtils.addDays(new Date(), -1));
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setEndDate(DateUtils.addDays(new Date(), 1));
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		reportItem = dao().getTestRequestItemsByTestRequestId(Collections.singletonList(testRequest.getId()), false)
				.stream()
				.findFirst().orElse(null);
		testRequestReportItemFilter.setTestConceptId(reportItem.getOrder().getConcept().getConceptId());
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setApproverUserId(Context.getAuthenticatedUser().getUserId());
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() == 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setLimit(1);
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setStartIndex(0);
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setTestRequestItemIdMin(reportItem.getId()-1);
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setTestRequestIdMin(testRequest.getId() - 1);
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setReferenceNumber(result.getData().get(0).getAccessionNumber());
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);

		testRequestReportItemFilter=new TestRequestReportItemFilter();
		testRequestReportItemFilter.setPatientId(testRequest.getPatient().getId());
		testRequestReportItemFilter.setDiagonisticLocationId(worksheet.getAtLocation().getId());
		testRequestReportItemFilter.setStartDate(DateUtils.addDays(new Date(), -1));
		testRequestReportItemFilter.setEndDate(DateUtils.addDays(new Date(), 1));
		reportItem = dao().getTestRequestItemsByTestRequestId(Collections.singletonList(testRequest.getId()), false)
				.stream()
				.findFirst().orElse(null);
		testRequestReportItemFilter.setTestConceptId(reportItem.getOrder().getConcept().getConceptId());
		testRequestReportItemFilter.setReferralLocationId(testRequest.getReferralFromFacility().getId());
		testRequestReportItemFilter.setLimit(1);
		testRequestReportItemFilter.setStartIndex(0);
		testRequestReportItemFilter.setTestRequestItemIdMin(reportItem.getId()-1);
		testRequestReportItemFilter.setTestRequestIdMin(testRequest.getId()-1);
		reportResult = labManagementService.findSampleCustodyReportItems(testRequestReportItemFilter);
		Assert.assertTrue(reportResult.getData().size() > 0);
	}

	@Test
	public void checkDataMigration() throws JsonProcessingException {
		GlobalProperties.setGlobalProperty(GlobalProperties.ENABLE_DATA_MIGRATION, "true");
		eu().setRequiredTestRequestEnvironment();
		BatchJobDTO batchJob=new BatchJobDTO();
		batchJob.setBatchJobType(BatchJobType.Migration);
		batchJob.setStatus(BatchJobStatus.Pending);
		batchJob.setDescription("Batch job");
		labManagementService.saveBatchJob(batchJob);

		AsyncTasksBatchJob asyncTasksBatchJob=new AsyncTasksBatchJob();
		asyncTasksBatchJob.execute();
	}

	@Test
	public void testResultImportConfigTest(){
		TestResultImportConfig testResultImportConfig= eu().newTestResultImportConfig(dao());
		labManagementService.saveTestResultImportConfig(testResultImportConfig);

		TestResultImportConfig newtestResultImportConfig= eu().newTestResultImportConfig(dao());
		newtestResultImportConfig.setTest(testResultImportConfig.getTest());
		newtestResultImportConfig.setHeaderHash(testResultImportConfig.getHeaderHash());
		labManagementService.saveTestResultImportConfig(newtestResultImportConfig);

		Context.flushSession();
		Context.flushSession();

		testResultImportConfig = dao().getTestResultImportConfigByUuid(testResultImportConfig.getUuid());
		dao().getSession().refresh(testResultImportConfig);
		Assert.assertTrue(testResultImportConfig.getVoided());
	}
	@Test
	public void saveStorage_shouldSucceedOnAllFilters() {
		Storage storage = eu().newStorage(dao());
		StorageDTO dto = new StorageDTO();
		dto.setName(storage.getName());
		dto.setAtLocationUuid(storage.getAtLocation().getUuid());
		dto.setDescription(storage.getDescription());
		dto.setActive(storage.getActive());
		dto.setCapacity(81);

		Storage entity = labManagementService.saveStorage(dto);
		Context.flushSession();

		dto = new StorageDTO();
		dto.setUuid(entity.getUuid());
		dto.setName(storage.getName());
		dto.setAtLocationUuid(storage.getAtLocation().getUuid());
		dto.setDescription(storage.getDescription());
		dto.setActive(storage.getActive());
		dto.setCapacity(81);
		entity = labManagementService.saveStorage(dto);
		Context.flushSession();

		StorageSearchFilter storageSearchFilter = new StorageSearchFilter();
		storageSearchFilter.setStorageId(entity.getId());
		Result<StorageUnitDTO> storageUnits = labManagementService.findStorageUnits(storageSearchFilter);
		Assert.assertEquals(storageUnits.getData().size(), 81);

		dto.setCapacity(10);
		entity = labManagementService.saveStorage(dto);
		Context.flushSession();

		storageSearchFilter = new StorageSearchFilter();
		storageSearchFilter.setStorageId(entity.getId());
		storageUnits = labManagementService.findStorageUnits(storageSearchFilter);
		Assert.assertEquals(storageUnits.getData().size(), 10);

		Storage storage1 = labManagementService.getStorageById(entity.getId());
		Sample sample = eu().newSample(dao());
		sample.setStorageUnit(labManagementService.getStorageUnitById(storageUnits.getData().get(0).getId()));
		dao().saveSample(sample);

		dto.setCapacity(9);
		StorageDTO finalDto = dto;
		Assert.assertThrows(LabManagementException.class, ()-> labManagementService.saveStorage(finalDto));

	}

	@Test
	public void findStorage_shouldSucceedOnAllFilters() {
		Storage storage = eu().newStorage(dao());
		StorageDTO dto = new StorageDTO();
		dto.setName(storage.getName());
		dto.setAtLocationUuid(storage.getAtLocation().getUuid());
		dto.setDescription(storage.getDescription());
		dto.setActive(storage.getActive());
		dto.setCapacity(10);

		Storage entity = labManagementService.saveStorage(dto);
		Context.flushSession();
		Context.flushSession();

		StorageSearchFilter filter = new StorageSearchFilter();
		filter.setStorageId(entity.getId());
		Result<StorageDTO> result = labManagementService.findStorages(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);

		filter = new StorageSearchFilter();
		filter.setVoided(!entity.getVoided());
		result = labManagementService.findStorages(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		filter = new StorageSearchFilter();
		filter.setVoided(entity.getVoided());
		result = labManagementService.findStorages(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		StorageDTO dbDto = result.getData().get(0);
		filter = new StorageSearchFilter();
		filter.setSearchText(dbDto.getName());
		result = labManagementService.findStorages(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 1);
		filter = new StorageSearchFilter();
		filter.setSearchText(UUID.randomUUID().toString());
		result = labManagementService.findStorages(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getId().equals(entity.getId())));
		assertEquals(result.getData().size(), 0);

		filter = new StorageSearchFilter();
		filter.setLimit(10);
		result = labManagementService.findStorages(filter);
		assertTrue(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertFalse(result.getData().isEmpty());

		filter = new StorageSearchFilter();
		filter.setLimit(10);
		filter.setStartIndex(Integer.MAX_VALUE / 1000);
		result = labManagementService.findStorages(filter);
		assertFalse(result.getData().stream().anyMatch(p -> p.getUuid().equalsIgnoreCase(entity.getUuid()) && entity.getVoided().equals(p.getVoided())));
		assertTrue(result.getData().isEmpty());

		labManagementService.deleteStorage(entity.getUuid());
		filter = new StorageSearchFilter();
		filter.setLimit(10);
		result = labManagementService.findStorages(filter);
		assertTrue(result.getData().isEmpty());
	}

}
