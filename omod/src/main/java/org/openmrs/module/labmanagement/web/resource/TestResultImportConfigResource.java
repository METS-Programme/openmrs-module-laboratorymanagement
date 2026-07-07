package org.openmrs.module.labmanagement.web.resource;

import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.properties.*;
import io.swagger.models.properties.StringProperty;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import org.openmrs.module.labmanagement.api.reporting.GenericObject;
import org.openmrs.module.labmanagement.api.utils.FileUtil;
import org.openmrs.module.labmanagement.api.utils.GlobalProperties;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.representation.*;
import org.openmrs.module.webservices.rest.web.resource.api.Uploadable;
import org.openmrs.module.labmanagement.api.ModuleConstants;
import org.openmrs.module.labmanagement.api.LabManagementService;
import org.openmrs.module.labmanagement.api.model.*;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.web.multipart.MultipartFile;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVParserBuilder;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@Resource(name = RestConstants.VERSION_1 + "/" + ModuleConstants.MODULE_ID + "/test-result-import-config", supportedClass = TestResultImportConfig.class, supportedOpenmrsVersions = {
        "1.9.*", "1.10.*", "1.11.*", "1.12.*", "2.*"})
public class TestResultImportConfigResource extends ResourceBase<TestResultImportConfig> implements Uploadable {

    @Override
    public TestResultImportConfig getByUniqueId(String uniqueId) {
         throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    protected void delete(TestResultImportConfig delegate, String reason, RequestContext context) throws ResponseException {
         throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    protected PageableResult doSearch(RequestContext context) {
         throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
         throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public TestResultImportConfig newDelegate() {
        return new TestResultImportConfig();
    }

    @Override
    public TestResultImportConfig save(TestResultImportConfig delegate) {
        if(delegate.getTest() == null){
            throw new IllegalRequestException("Test not set");
        }
        if(delegate.getTest().getUuid().equalsIgnoreCase(delegate.getUuid())){
            return delegate;
        }
        return getLabManagementService().saveTestResultImportConfig(delegate);
    }

    @Override
    public void purge(TestResultImportConfig delegate, RequestContext context) throws ResponseException {
        delete(delegate, null, context);
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("test");
        description.addProperty("fieldMapping");
        return description;
    }

    @PropertySetter("fieldMapping")
    public void setFieldMapping(TestResultImportConfig instance, Map<String, ?> items) throws IOException {
        String session = (String)items.get("session");
        if(StringUtils.isBlank(session)) {
            throw new IllegalRequestException("Import session not set");
        }
        items.remove("session");

        String testUuid = (String) items.get("concept");
        if(StringUtils.isBlank(testUuid)){
            throw new IllegalRequestException("Import session concept set");
        }
        items.remove("concept");

        Concept test = Context.getConceptService().getConceptByUuid(testUuid);
        if(test == null){
            throw new IllegalRequestException("Import session concept invalid");
        }

        String fileUuid = null;
        String importConfigUuid = null;
        if(session.startsWith("new-")) {
            if (session.length() < 5) {
                throw new IllegalRequestException("Import session invalid");
            }
            fileUuid = session.substring(4);
        }else{
            if(session.indexOf('/') < 0){
                throw new IllegalRequestException("Import session invalid");
            }
            String[] sessionParts = session.split("/");
            if(sessionParts.length != 2){
                throw new IllegalRequestException("Import session malformed");
            }
            fileUuid = sessionParts[1];
            importConfigUuid = sessionParts[0];
        }

        String fileName = Context.getAuthenticatedUser().getUserId().toString() + "_" + fileUuid;
        File workingDir = FileUtil.getWorkingDirectory();
        File filePath = new File(workingDir, fileName);
        if(!filePath.exists()) {
            throw new IllegalRequestException("Session file no longer exists");
        }

        String[] headers = null;
        String[] firstResultRow = null;
        String separator = (String) items.get("separator");
        if(StringUtils.isBlank(separator)){
            separator = ",";
        }
        String quote = (String) items.get("quoteChar");
        if(StringUtils.isBlank(quote)){
            quote = "\"";
        }

        try(InputStream inputStream = Files.newInputStream(filePath.toPath())) {
            BOMInputStream bomInputStream = new BOMInputStream(inputStream, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE);
            Charset charset;
            if (!bomInputStream.hasBOM()) charset = StandardCharsets.UTF_8;
            else if (bomInputStream.hasBOM(ByteOrderMark.UTF_8)) charset = StandardCharsets.UTF_8;
            else if (bomInputStream.hasBOM(ByteOrderMark.UTF_16LE)) charset = StandardCharsets.UTF_16LE;
            else if (bomInputStream.hasBOM(ByteOrderMark.UTF_16BE)) charset = StandardCharsets.UTF_16BE;
            else {
                throw new IllegalRequestException("The charset of the file is not supported.");
            }

            try (Reader streamReader = new InputStreamReader(bomInputStream, charset);
                 BufferedReader bufferedReader = new BufferedReader(streamReader);) {
                CSVReader csvReader = new CSVReaderBuilder(bufferedReader).withCSVParser(new CSVParserBuilder().withSeparator(separator.charAt(0)).withQuoteChar(quote.charAt(0)).build()).build();
                if ((headers = csvReader.readNext()) == null) {
                    throw new IllegalRequestException("File has no headers");
                }
            }
        }



        String headerHash =  DigestUtils.md5Hex(separator + "|||" + quote + "|||" + Arrays.stream(headers).collect(Collectors.joining("|||")));
        TestResultImportConfig testResultImportConfig = getLabManagementService().getTestResultImportConfigByHeaderHash(headerHash, test);
        Collection<Map<?, ?>> newHeaders  = (Collection<Map<?, ?>>) items.get("headers");
        for(Map<?, ?> map : newHeaders){
            if(newHeaders.contains("value")){
                newHeaders.remove(map);
            }
        }
        String mappingJson = GenericObject.toJson(items);
        if(testResultImportConfig != null){
            String currentHash =  DigestUtils.md5Hex(testResultImportConfig.getFieldMapping());
            String newHash = DigestUtils.md5Hex(mappingJson);
            if(currentHash.equals(newHash)){
                instance.setUuid(test.getUuid());
                instance.setFieldMapping(testResultImportConfig.getFieldMapping());
                instance.setHeaderHash(headerHash);
                instance.setTest(test);
                return;
            }
        }

        instance.setHeaderHash(headerHash);
        instance.setFieldMapping(mappingJson);
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
            description.addProperty("uuid");
            description.addProperty("dateCreated");
            description.addProperty("dateChanged");
            description.addProperty("headerHash");
            description.addProperty("fieldMapping");
            description.addProperty("test", new NamedRepresentation("fullchildren"));
        }

        if (rep instanceof DefaultRepresentation) {
            description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
        }

        if (rep instanceof FullRepresentation) {
            description.addProperty("creator", Representation.REF);
            description.addProperty("changedBy", Representation.REF);
            description.addSelfLink();
        }

        if (rep instanceof RefRepresentation) {
            description.addProperty("uuid");
        }

        return description;
    }

@Override
	public Model getGETModel(Representation rep) {
    ModelImpl modelImpl = (ModelImpl) super.getGETModel(rep);
    if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation) {
        modelImpl.property("uuid", new StringProperty());
        modelImpl.property("dateCreated", new DateTimeProperty());
        modelImpl.property("dateChanged", new DateTimeProperty());
        modelImpl.property("headerHash", new StringProperty());
        modelImpl.property("fieldMapping", new StringProperty());
        modelImpl.property("test", new RefProperty("#/definitions/ConceptGetRef"));
    }

    if (rep instanceof FullRepresentation) {
        modelImpl.property("creator", new RefProperty("#/definitions/UserGetRef"));
        modelImpl.property("changedBy", new RefProperty("#/definitions/UserGetRef"));
    }

    if (rep instanceof RefRepresentation) {
        modelImpl.property("uuid", new StringProperty());
    }

    return modelImpl;
}

    @Override
    public Object upload(MultipartFile file, RequestContext context) throws ResponseException, IOException {
        String testUuid = context.getParameter("testUuid");
        if (testUuid == null) {
            throw new IllegalRequestException("Test must be included in a request parameter named 'testUuid'.");
        }

        if(file == null){
            throw new IllegalRequestException("File must be included in a request parameter named.");
        }

        Concept concept = Context.getConceptService().getConceptByUuid(testUuid);
        if(concept == null){
            throw new IllegalRequestException("Test not found");
        }

        LabManagementService labManagementService = getLabManagementService();
        TestConfig testConfig = labManagementService.getTestConfigByConcept(concept.getId());
        if(testConfig == null){
            throw new IllegalRequestException("Test configuration not found");
        }

        long maximumFileSize = GlobalProperties.getTestResultsMaxUploadSize();
        if (file.getSize() > ( maximumFileSize* 1024 * 1024)) {
            throw new IllegalRequestException(
                    String.format(
                            Context.getMessageSourceService().getMessage("labmanagement.importmaxfilesizeexceeded"),
                            maximumFileSize));
        }

        String contentType = file.getContentType();
        if(contentType == null || (!"text/csv".equals(contentType.toLowerCase()) && !"application/vnd.ms-excel".equals(contentType.toLowerCase()))){
            throw new IllegalRequestException(Context.getMessageSourceService().getMessage("labmanagement.importcontenttypenotsupported"));
        }

        String fileUuid = UUID.randomUUID().toString();
        File workingDir = FileUtil.getWorkingDirectory();
        String fileName = Context.getAuthenticatedUser().getUserId().toString() + "_" + fileUuid;
        File filePath = new File(workingDir, fileName);
        try

        {
            file.transferTo(filePath);
        } catch (
                Exception exception
        )

        {
            throw new IllegalRequestException(Context.getMessageSourceService().getMessage("labmanagement.importtransferworkingdirfailed"));
        }
        String[] headers = null;
        String[] firstResultRow = null;
        String separator = context.getParameter("separator");
        if(StringUtils.isBlank(separator)){
            separator = ",";
        }
        String quote = context.getParameter("quoteChar");
        if(StringUtils.isBlank(quote)){
            quote = "\"";
        }

        List<String[]> csvRows = new ArrayList<>();
        try(InputStream inputStream = Files.newInputStream(filePath.toPath())) {
            BOMInputStream bomInputStream = new BOMInputStream(inputStream, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE);
            Charset charset;
            if (!bomInputStream.hasBOM()) charset = StandardCharsets.UTF_8;
            else if (bomInputStream.hasBOM(ByteOrderMark.UTF_8)) charset = StandardCharsets.UTF_8;
            else if (bomInputStream.hasBOM(ByteOrderMark.UTF_16LE)) charset = StandardCharsets.UTF_16LE;
            else if (bomInputStream.hasBOM(ByteOrderMark.UTF_16BE)) charset = StandardCharsets.UTF_16BE;
            else {
                throw new IllegalRequestException("The charset of the file is not supported.");
            }

            try (Reader streamReader = new InputStreamReader(bomInputStream, charset);
                 BufferedReader bufferedReader = new BufferedReader(streamReader);) {
                CSVReader csvReader = new CSVReaderBuilder(bufferedReader).withCSVParser(new CSVParserBuilder().withSeparator(separator.charAt(0)).withQuoteChar(quote.charAt(0)).build()).build();
                if ((headers = csvReader.readNext()) == null) {
                    throw new IllegalRequestException("File has no headers");
                }
                if ((firstResultRow = csvReader.readNext()) == null) {
                    throw new IllegalRequestException("File has records");
                }
                do {
                    csvRows.add(firstResultRow);
                } while ((firstResultRow = csvReader.readNext()) != null);
            }
        }

        String headerHash =  DigestUtils.md5Hex(separator + "|||" + quote + "|||" + Arrays.stream(headers).collect(Collectors.joining("|||")));
        TestResultImportConfig testResultImportConfig = getLabManagementService().getTestResultImportConfigByHeaderHash(headerHash, testConfig.getTest());
        if(testResultImportConfig == null){
            testResultImportConfig= new TestResultImportConfig();
            testResultImportConfig.setUuid("new-"+fileUuid);
            testResultImportConfig.setTest(concept);
            testResultImportConfig.setHeaderHash(headerHash);
            int position = 0;
            List<GenericObject> headerList = new ArrayList<>();
            for(String header: headers){
                GenericObject headerObject = new GenericObject();
                headerObject.put("name", header);
                headerObject.put("index", position);
                headerObject.put("value", csvRows.get(0)[position]);
                headerList.add(headerObject);
                position++;
            }
            GenericObject genericObject = new GenericObject();
            genericObject.put("headers", headerList);
            genericObject.put("separator", separator);
            genericObject.put("quote", quote);
            genericObject.put("rows", csvRows);
            testResultImportConfig.setFieldMapping(GenericObject.toJson(genericObject));
        }else{
            GenericObject genericObject = GenericObject.parseJson(testResultImportConfig.getFieldMapping());
            Collection<Map<String,Object>> rowHeaders = ((Collection<Map<String,Object>>)genericObject.get("headers"));
            int position = 0;
            for(String header: headers){
                Optional<Map<String,Object>> dbHeader =  rowHeaders.stream().filter(p-> header.equals(p.get("name"))).findFirst();
                if(dbHeader.isPresent()){
                    dbHeader.get().put("value", csvRows.get(0)[position]);
                }
            }
            genericObject.put("rows", csvRows);
            testResultImportConfig.setFieldMapping(GenericObject.toJson(genericObject));
            testResultImportConfig.setUuid(testResultImportConfig.getUuid() + "/" + fileUuid);
        }


        return (SimpleObject) ConversionUtil.convertToRepresentation(testResultImportConfig, Representation.DEFAULT);
    }


}
