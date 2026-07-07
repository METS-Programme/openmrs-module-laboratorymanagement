package org.openmrs.module.labmanagement.api.jobs;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParserBuilder;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import org.openmrs.module.labmanagement.api.LabManagementException;
import org.openmrs.module.labmanagement.api.dto.ImportResult;
import org.openmrs.module.labmanagement.api.LabManagementService;
import org.openmrs.module.labmanagement.api.dto.*;
import org.openmrs.module.labmanagement.api.model.ApprovalFlow;
import org.openmrs.module.labmanagement.api.model.TestConfig;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TestConfigImportJob {

	private final Path file;

	private final boolean hasHeader;

	private final ImportResult result = new ImportResult();

	private int batchSize = 50;

	private final int GROUP_CONCEPT_ID = 0;

    private final int TEST_CONCEPT_ID = 2;

    private final int REQUIRES_APPROVAL = 4;

	private final int APPROVAL_FLOW = 5;

	private final int ACTIVE = 6;

	public TestConfigImportJob(Path file, boolean hasHeader) {
        this.file = file;
        this.hasHeader = hasHeader;
        result.setErrors(new ArrayList<>());
    }

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		if (batchSize > 0) {
			this.batchSize = batchSize;
		}
	}

	private boolean isBlank(String value) {
		return StringUtils.isBlank(value) || value.equalsIgnoreCase("null");
	}

	private Object validateLine(String[] line) {
        if (line == null || line.length == 0) return null;
        Object[] objects = new Object[8];
        List<String> errors = new ArrayList<>();
        if (line.length < 3) {
            errors.add(Context.getMessageSourceService().getMessage("labmanagement.importminimumfields"));
            return errors;
        }
        if (!isBlank(line[GROUP_CONCEPT_ID])) {
            try {
                Integer value = Integer.parseInt(line[GROUP_CONCEPT_ID]);
                if (value > 0) {
                    objects[GROUP_CONCEPT_ID] = value;
                } else {
                    errors.add(Context.getMessageSourceService().getMessage("labmanagement.importgroupidpositive"));
                }
            } catch (Exception ex) {
                errors.add(Context.getMessageSourceService().getMessage("labmanagement.importgroupidnumber"));
            }
        }else{
            errors.add(Context.getMessageSourceService().getMessage("labmanagement.importgroupconceptidrequired"));
        }

        if (!isBlank(line[TEST_CONCEPT_ID])) {
            try {
                Integer value = Integer.parseInt(line[TEST_CONCEPT_ID]);
                if (value > 0) {
                    objects[TEST_CONCEPT_ID] = value;
                } else {
                    errors.add(Context.getMessageSourceService().getMessage("labmanagement.importtestconceptidpositive"));
                }
            } catch (Exception ex) {
                errors.add(Context.getMessageSourceService().getMessage("labmanagement.importtestconceptidnumber"));
            }
        } else if (objects[GROUP_CONCEPT_ID] == null) {
            errors.add(Context.getMessageSourceService().getMessage("labmanagement.importtestconceptidrequired"));
        }

        if (!isBlank(line[REQUIRES_APPROVAL])) {
            Boolean value = null;
            String token = line[REQUIRES_APPROVAL].toLowerCase();
            if (token.equals("1") || token.equals("yes")) {
                value = true;
            } else if (token.equals("0") || token.equals("no")) {
                value = false;
            }
            if (value != null) {
                objects[REQUIRES_APPROVAL] = value;
            } else {
                errors.add(Context.getMessageSourceService().getMessage("labmanagement.importrequiresapprovalinvalid"));
            }
        }else{
            errors.add(Context.getMessageSourceService().getMessage("labmanagement.importrequireapprovalrequired"));
        }

        if(objects[REQUIRES_APPROVAL] != null &&  (boolean)objects[REQUIRES_APPROVAL]) {
            if (!isBlank(line[APPROVAL_FLOW])) {
                objects[APPROVAL_FLOW] = line[APPROVAL_FLOW];
            }else{
                errors.add(Context.getMessageSourceService().getMessage("labmanagement.importapprovalflowrequired"));
            }
        }

        if (!isBlank(line[ACTIVE])) {
            Boolean value = null;
            String token = line[ACTIVE].toLowerCase();
            if (token.equals("1") || token.equals("yes")) {
                value = true;
            } else if (token.equals("0") || token.equals("no")) {
                value = false;
            }
            if (value != null) {
                objects[ACTIVE] = value;
            } else {
                errors.add(Context.getMessageSourceService().getMessage("labmanagement.importactiveinvalid"));
            }
        }else{
            errors.add(Context.getMessageSourceService().getMessage("labmanagement.importactiverequired"));
        }

        return errors.isEmpty() ? objects : errors;
    }

	@SuppressWarnings({ "unchecked" })
	private void updateTestConfigs(Map<Integer, Object[]> testConfigItems) {
        LabManagementService labManagementService = Context.getService(LabManagementService.class);

        TestConfigSearchFilter testConfigSearchFilter=new TestConfigSearchFilter();
        testConfigSearchFilter.setTestIds(testConfigItems.values().stream().map(p-> (Integer)p[TEST_CONCEPT_ID]).collect(Collectors.toList()));
        List<TestConfigDTO> testConfigDtos = labManagementService.findTestConfigurations(testConfigSearchFilter).getData();

        // Get the rowIds for existing lab items
        Map<Object, List<Object[]>> rowsToUpdate = testConfigItems.entrySet().stream().map(p -> new Object[]{p.getKey(),
                testConfigDtos.stream().filter(x -> (p.getValue()[TEST_CONCEPT_ID].equals(x.getTestId()))
                ).findFirst()
        }).filter(p -> ((Optional<?>) p[1]).isPresent()).collect(Collectors.groupingBy(p -> p[0]));

        List<Map.Entry<Integer, Object[]>> newTestConfigs = testConfigItems.entrySet().stream().filter(p -> !rowsToUpdate.containsKey(p.getKey())).collect(Collectors.toList());
        List<Map.Entry<Integer, Object[]>> rowsToCreate = new ArrayList<>();

        if (!newTestConfigs.isEmpty()) {
            // validate minimum information required to be created.
            rowsToCreate.addAll(newTestConfigs);
        }

        if (rowsToUpdate.isEmpty() && rowsToCreate.isEmpty()) {
            return;
        }

        // prefetch the concepts and approval flows
        List<String> approvalFlowIds = testConfigItems.values().stream().map(p -> p[APPROVAL_FLOW]).filter(Objects::nonNull).map(p -> (String) p).distinct().collect(Collectors.toList());

//                rowsToCreate.stream().map(p -> p.getValue()[APPROVAL_FLOW]).filter(Objects::nonNull).map(p -> (String) p).distinct().collect(Collectors.toList());
//        approvalFlowIds.addAll(rowsToUpdate.values().stream().flatMap(p -> p[0]).map(p -> p.getValue()[APPROVAL_FLOW]).filter(Objects::nonNull).map(p -> (String) p).distinct().collect(Collectors.toList());)

        List<Integer> conceptIds = testConfigItems.values().stream().map(objects -> Arrays.asList(
                objects[TEST_CONCEPT_ID],
                objects[GROUP_CONCEPT_ID]
        )).flatMap(Collection::stream).filter(Objects::nonNull).map(p -> (Integer) p).distinct().collect(Collectors.toList());

        Map<String, List<ApprovalFlow>> approvalFlows = labManagementService.getApprovalFlowsBySystemName(approvalFlowIds).stream().collect(Collectors.groupingBy(p->((ApprovalFlow) p).getSystemName().toLowerCase()));
        Map<Integer, List<Concept>> concepts = labManagementService.getConcepts(conceptIds).stream().collect(Collectors.groupingBy(Concept::getConceptId));

        Map<Integer, List<TestConfig>> labItemsToUpdate = null;
        Map<Integer, TestConfig> prevTestConfigConcepts = null;

        if (!rowsToUpdate.isEmpty()) {
            labItemsToUpdate = labManagementService.getTestConfigsByIds(testConfigDtos.stream().map(TestConfigDTO::getId).collect(Collectors.toList()))
                    .stream().collect(Collectors.groupingBy(TestConfig::getId));
        }

        prevTestConfigConcepts = new HashMap<>();

        for (Map.Entry<Integer, Object[]> recordToProcess : testConfigItems.entrySet()) {
            Object[] valuesToProcess = null;
            boolean isNewRecord = true;
            if (rowsToUpdate.containsKey(recordToProcess.getKey())) {
                valuesToProcess = recordToProcess.getValue();
                isNewRecord = false;
            } else {
                Optional<Map.Entry<Integer, Object[]>> rowFound = rowsToCreate.stream().filter(p -> p.getKey().equals(recordToProcess.getKey())).findFirst();
                if (rowFound.isPresent()) {
                    valuesToProcess = rowFound.get().getValue();
                }
            }
            if (valuesToProcess == null) {
                continue;
            }

            boolean isVeryNewRecordTestConfig = false;
            TestConfig testConfig = null;
            Object[] updates = valuesToProcess;
            if (isNewRecord) {
                boolean isNew = true;
                testConfig = prevTestConfigConcepts.getOrDefault((Integer) updates[TEST_CONCEPT_ID], null);
                if (testConfig != null) {
                    isNew = false;
                } else {
                    isVeryNewRecordTestConfig = true;
                    testConfig = new TestConfig();
                    List<Concept> conceptCollection = concepts.getOrDefault((Integer) updates[TEST_CONCEPT_ID], null);
                    if (conceptCollection == null) {
                        result.getErrors().add(String.format("Row %1s: %2s", recordToProcess.getKey(),
                                String.format(Context.getMessageSourceService().getMessage("labmanagement.importconceptnofound"),
                                        updates[TEST_CONCEPT_ID].toString())));
                        continue;
                    }
                    testConfig.setTest(conceptCollection.get(0));
                }

                if (isNew) {
                    testConfig.setCreator(Context.getAuthenticatedUser());
                    testConfig.setDateCreated(new Date());
                }
            } else {
                List<Object[]> rowToUpdate = rowsToUpdate.get(recordToProcess.getKey());
                List<TestConfig> labItemCollection = labItemsToUpdate.getOrDefault(((Optional<TestConfigDTO>) (rowToUpdate.get(0)[1])).get().getId(), null);
                if (labItemCollection != null) {
                    testConfig = labItemCollection.get(0);
                }
            }

            if (testConfig == null) {
                result.getErrors().add(String.format("Row %1s: %2s", recordToProcess.getKey(), Context.getMessageSourceService().getMessage("labmanagement.importtestconfigmismatch")));
                continue;
            }

            Boolean activeToSet = null;
            Boolean requireApprovalToSet = null;
            Concept testGroupToSet = null;
            ApprovalFlow approvalFlowToSet = null;

            if (updates[GROUP_CONCEPT_ID] != null && (testConfig.getTestGroup() == null || !testConfig.getTestGroup().getId().equals(updates[GROUP_CONCEPT_ID]))) {
                List<Concept> conceptCollection = concepts.getOrDefault((Integer) updates[GROUP_CONCEPT_ID], null);
                if (conceptCollection == null) {
                    result.getErrors().add(String.format("Row %1s: %2s", recordToProcess.getKey(),
                            String.format(Context.getMessageSourceService().getMessage("labmanagement.importconceptnofound"),
                                    updates[GROUP_CONCEPT_ID].toString())));
                    continue;
                }
                testGroupToSet = conceptCollection.get(0);
            }

            if (updates[REQUIRES_APPROVAL] != null && !updates[REQUIRES_APPROVAL].equals(testConfig.getRequireApproval())) {
                requireApprovalToSet = (Boolean) updates[REQUIRES_APPROVAL];
            }

            if (updates[ACTIVE] != null && !updates[ACTIVE].equals(testConfig.getEnabled())) {
                activeToSet = (Boolean) updates[ACTIVE];
            }
            if (updates[APPROVAL_FLOW] != null) {
                if ((testConfig.getApprovalFlow() == null || !testConfig.getApprovalFlow().getSystemName().equalsIgnoreCase((String) updates[APPROVAL_FLOW]))) {
                    List<ApprovalFlow> approvalFlowsOrNUll = approvalFlows.getOrDefault(((String) updates[APPROVAL_FLOW]).toLowerCase(), null);
                    if (approvalFlowsOrNUll == null) {
                        result.getErrors().add(String.format("Row %1s: %2s", recordToProcess.getKey(),
                                String.format(Context.getMessageSourceService().getMessage("labmanagement.importapprovalflownofound"),
                                        updates[ACTIVE].toString())));
                        continue;
                    }
                    approvalFlowToSet = approvalFlowsOrNUll.get(0);
                }
            }

            boolean saveTestConfig = false;

            if (activeToSet != null) {
                testConfig.setEnabled(activeToSet);
                saveTestConfig = true;
            }
            if (requireApprovalToSet != null) {
                testConfig.setRequireApproval(requireApprovalToSet);
                saveTestConfig = true;
            }

            if(testConfig.getRequireApproval() != null){
                if(testConfig.getRequireApproval()) {
                    if(approvalFlowToSet != null) {
                        testConfig.setApprovalFlow(approvalFlowToSet);
                        saveTestConfig = true;
                    }
                }else if(testConfig.getApprovalFlow() != null){
                    testConfig.setApprovalFlow(null);
                    saveTestConfig = true;
                }
            }

            if (testGroupToSet != null) {
                testConfig.setTestGroup(testGroupToSet);
                saveTestConfig = true;
            }


            if (saveTestConfig) {
                testConfig.setDateChanged(new Date());
                testConfig.setChangedBy(Context.getAuthenticatedUser());
                labManagementService.saveTestConfig(testConfig);
                if (isVeryNewRecordTestConfig) {
                    result.setCreatedCount(result.getCreatedCount() + 1);
                    prevTestConfigConcepts.put((Integer) updates[TEST_CONCEPT_ID], testConfig);
                } else {
                    result.setUpdatedCount(result.getUpdatedCount() + 1);
                    prevTestConfigConcepts.put((Integer) updates[TEST_CONCEPT_ID], testConfig);
                }
            } else{
                result.setNotChangedCount(result.getNotChangedCount() + 1);
            }
        }
    }

	@SuppressWarnings({ "unchecked" })
	public void execute() {
        CSVReader csvReader = null;
        int row = 0;
        boolean hasErrors = false;
        try {
            try(Writer writer = Files.newBufferedWriter( new File(file.toString() + "_errors").toPath())) {
                try(InputStream inputStream = Files.newInputStream(file)) {
                    BOMInputStream bomInputStream = new BOMInputStream(inputStream, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE);
                    Charset charset;
                    if (!bomInputStream.hasBOM()) charset = StandardCharsets.UTF_8;
                    else if (bomInputStream.hasBOM(ByteOrderMark.UTF_8)) charset = StandardCharsets.UTF_8;
                    else if (bomInputStream.hasBOM(ByteOrderMark.UTF_16LE)) charset = StandardCharsets.UTF_16LE;
                    else if (bomInputStream.hasBOM(ByteOrderMark.UTF_16BE)) charset = StandardCharsets.UTF_16BE;
                    else {
                        throw new LabManagementException("The charset of the file is not supported.");
                    }

                    try (Reader streamReader = new InputStreamReader(bomInputStream, charset);
                         BufferedReader bufferedReader = new BufferedReader(streamReader);) {
                    csvReader = new CSVReaderBuilder(bufferedReader).withCSVParser(new CSVParserBuilder().withSeparator(',').withQuoteChar('\"').build()).build();
                    if (hasHeader) {
                        csvReader.skip(1);
                    }
                    String[] csvLine = null;
                    boolean processedPending = false;
                    Map<Integer, Object[]> list = new HashMap<>();
                    while ((csvLine = csvReader.readNext()) != null) {
                        row++;
                        processedPending = false;
                        if (result.getErrors().size() > 10) {
                            hasErrors = true;
                            for (String error : result.getErrors()) {
                                writer.append(error);
                                writer.append("\r\n");
                            }
                            result.getErrors().clear();
                        }
                        Object validationResult = validateLine(csvLine);
                        if (validationResult == null) {
                            continue;
                        } else if (validationResult instanceof List<?>) {
                            List<String> errors = (List<String>) validationResult;
                            result.getErrors().add(String.format("Row %1s: %2s", row, String.join(", ", errors)));
                            continue;
                        }

                        list.put(row, (Object[]) validationResult);
                        if (list.size() == getBatchSize()) {
                            updateTestConfigs(list);
                            processedPending = true;
                            list.clear();
                        }

                    }
                    if (!processedPending) {
                        updateTestConfigs(list);
                    }
                }
                if (hasErrors) {
                    for (String error : result.getErrors()) {
                        writer.append(error);
                        writer.append("\r\n");
                    }
                    result.getErrors().clear();
                    result.getErrors().add(Context.getMessageSourceService().getMessage("labmanagement.importerrorswhileimporting"));
                    result.setHasErrorFile(true);
                } else if (result.getErrors().isEmpty()) {
                    result.setSuccess(true);
                }
            }
            }
        } catch (Exception exception) {
            result.getErrors().add(0, "Stopped processing at row " + Integer.toString(row));
            result.getErrors().add(exception.toString());
        } finally {
            if (csvReader != null) {
                try {
                    csvReader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

	public Object getResult() {
		return result;
	}
}
