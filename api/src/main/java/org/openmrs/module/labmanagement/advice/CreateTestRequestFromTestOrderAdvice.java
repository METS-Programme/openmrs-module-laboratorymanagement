package org.openmrs.module.labmanagement.advice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.labmanagement.api.LabManagementService;
import org.openmrs.module.labmanagement.api.model.TestRequest;
import org.openmrs.module.labmanagement.api.model.TestRequestItem;
import org.springframework.aop.AfterReturningAdvice;

import java.lang.reflect.Method;
import java.util.List;

public class CreateTestRequestFromTestOrderAdvice implements AfterReturningAdvice {

    private static final Log LOG = LogFactory.getLog(CreateTestRequestFromTestOrderAdvice.class);

    /**
     * This is called immediately an order is saved
     */
    @Override
    public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {

        try {
            if (method.getName().equals("saveOrder") && args.length > 0 && args[0] instanceof Order) {
                Order order = (Order) args[0];
                if (order instanceof TestOrder) {
                    LabManagementService service = Context.getService(LabManagementService.class);
                   List<TestRequestItem> testRequestItemList=service.getTestRequestItemByOrder(order);

                    if (testRequestItemList.isEmpty()) {
                        service.migrateOrder(order);
                    }

                }
            }
        } catch (Exception e) {
            LOG.error("Error intercepting order before creation: " + e.getMessage(), e);
        }
    }
}
