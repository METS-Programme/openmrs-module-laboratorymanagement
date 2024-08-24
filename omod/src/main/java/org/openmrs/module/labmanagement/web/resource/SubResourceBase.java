package org.openmrs.module.labmanagement.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.labmanagement.api.LabManagementService;
import org.openmrs.module.labmanagement.api.dto.Result;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingSubResource;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

public abstract class SubResourceBase<T, P, PR extends DelegatingCrudResource<P>> extends DelegatingSubResource<T, P, PR> {

	private LabManagementService labManagementService;

	protected LabManagementService getLabManagementService() {
		if (labManagementService == null) {
			labManagementService = Context.getService(LabManagementService.class);
		}
		return labManagementService;
	}

	protected void forbidden() {
		throw new RestClientException("403: Forbidden");
	}

	protected void notFound() {
		throw new RestClientException("404: Forbidden");
	}

	protected void invalidRequest(String messageKey, Object... args) {
		throw new RuntimeException(messageKey);
	}

	protected void requirePriviledge(String priviledge) {
		UserContext userContext = Context.getUserContext();
		if (!userContext.hasPrivilege(priviledge))
			forbidden();
	}

	protected String nullIfEmpty(String string) {
		if (string == null)
			return null;
		string = string.trim();
		if (string.isEmpty())
			return null;
		return string;
	}

	protected <E> PageableResult emptyResult(RequestContext context) {
		return new AlreadyPaged<E>(context, new ArrayList<E>(), false);
	}

	protected <E> AlreadyPaged<E> toAlreadyPaged(Result<E> result, RequestContext context) {
		return new AlreadyPaged<E>(context, result.getData(), result.hasMoreResults(), result.getTotalRecordCount());
	}

	protected <E> AlreadyPaged<E> toAlreadyPaged(List<E> result, RequestContext context) {
		return new AlreadyPaged<E>(context, result, false, (long) result.size());
	}
}
