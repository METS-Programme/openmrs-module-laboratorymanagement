package org.openmrs.module.labmanagement.web.controller;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.labmanagement.api.ModuleConstants;
import org.openmrs.module.labmanagement.api.LabManagementService;
import org.openmrs.module.labmanagement.api.dto.ImportResult;
import org.openmrs.module.labmanagement.api.utils.FileUtil;
import org.openmrs.module.labmanagement.api.utils.GlobalProperties;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

@Controller("${rootrootArtifactId}.TestConfigImportController")
@RequestMapping("/rest/" + RestConstants.VERSION_1 + "/" + ModuleConstants.MODULE_ID + "/test-config-import")
public class TestConfigImportController {

	@RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public ImportResult upload(@RequestParam(value = "file") MultipartFile file, HttpServletRequest request) {
        boolean authenticated = Context.isAuthenticated();
        if (!authenticated) {
            ImportResult importResult = new ImportResult();
            importResult.setSuccess(false);
            importResult.setErrors(new ArrayList<>());
            importResult.getErrors().add(Context.getMessageSourceService().getMessage("labmanagement.authrequired"));
            return importResult;
        }

        if (file == null) {
            ImportResult importResult = new ImportResult();
            importResult.setSuccess(false);
            importResult.setErrors(new ArrayList<>());
            importResult.getErrors().add(Context.getMessageSourceService().getMessage("labmanagement.importnofileuploaded"));
            return importResult;
        }

        if (file.getSize() > (GlobalProperties.getTestConfigsMaxUploadSize() * 1024 * 1024)) {
            ImportResult importResult = new ImportResult();
            importResult.setSuccess(false);
            importResult.setErrors(new ArrayList<>());
            importResult.getErrors().add(
                    String.format(
                            Context.getMessageSourceService().getMessage("labmanagement.importmaxfilesizeexceeded"),
                            GlobalProperties.getTestConfigsMaxUploadSize()));
            return importResult;
        }

        String contentType = file.getContentType();
        if(contentType == null || (!"text/csv".equals(contentType.toLowerCase()) &&
                !"application/vnd.ms-excel".equals(contentType.toLowerCase()))){
            ImportResult importResult = new ImportResult();
            importResult.setSuccess(false);
            importResult.setErrors(new ArrayList<>());
            importResult.getErrors().add(Context.getMessageSourceService().getMessage("labmanagement.importcontenttypenotsupported"));
            return importResult;
        }

        File workingDir = FileUtil.getWorkingDirectory();
        String fileName = Context.getAuthenticatedUser().getUserId().toString() + "_" + UUID.randomUUID().toString();
        File filePath = new File(workingDir, fileName);
        try

        {
            file.transferTo(filePath);
        } catch (
                Exception exception
                )

        {
            ImportResult importResult = new ImportResult();
            importResult.setSuccess(false);
            importResult.setErrors(new ArrayList<>());
            importResult.getErrors().add(Context.getMessageSourceService().getMessage("labmanagement.importtransferworkingdirfailed"));
            return importResult;
        }

        String hasHeaderParam = request.getParameter("hasHeader");
        boolean hasHeader = hasHeaderParam != null && (hasHeaderParam.toLowerCase().equals("true") || hasHeaderParam.toLowerCase().equals("1"));
        LabManagementService labManagementService = Context.getService(LabManagementService.class);
        ImportResult importResult = labManagementService.importTestConfigurations(filePath.toPath(), hasHeader);
        importResult.setUploadSessionId(fileName);
        return importResult;
    }

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public Object getErrors(@RequestParam(value = "id", required = true) String importSessionId, HttpServletResponse response)
	        throws IOException {
		boolean authenticated = Context.isAuthenticated();
		if (!authenticated) {
			return Context.getMessageSourceService().getMessage("labmanagement.authrequired");
		}

		int userIdPartEnds = -1;
		if (StringUtils.isBlank(importSessionId) || (userIdPartEnds = importSessionId.indexOf("_")) < 0) {
			return Context.getMessageSourceService().getMessage("labmanagement.importnofileuploaded");
		}

		Integer userId = null;
		if (userIdPartEnds > 0) {
			try {
				userId = Integer.parseInt(importSessionId.substring(0, userIdPartEnds));
			}
			catch (Exception exception) {}
		}

		if (userId == null || !Context.getAuthenticatedUser().getUserId().equals(userId)) {
			return Context.getMessageSourceService().getMessage("labmanagement.importnofileuploaded");
		}

		String uuidPart = null;
		if ((userIdPartEnds + 1) < importSessionId.length()) {
			try {
				uuidPart = importSessionId.substring(userIdPartEnds + 1);
				UUID.fromString(uuidPart);

			}
			catch (Exception exception) {
				uuidPart = null;
			}
		}
		if (uuidPart == null) {
			return Context.getMessageSourceService().getMessage("labmanagement.importnofileuploaded");
		}

		File workingDir = FileUtil.getWorkingDirectory();
		File fileName = new File(workingDir, userId.toString() + "_" + uuidPart + "_" + "errors");
		if (!fileName.exists()) {
			response.setStatus(404);
			return Context.getMessageSourceService().getMessage("labmanagement.importnofileuploaded");
		}

		response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
		FileSystemResource fileSystemResource = new FileSystemResource(fileName);
		return IOUtils.toByteArray(fileSystemResource.getInputStream());
	}
}
