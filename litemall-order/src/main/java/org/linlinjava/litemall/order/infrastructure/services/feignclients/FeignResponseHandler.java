package org.linlinjava.litemall.order.infrastructure.services.feignclients;

import com.google.protobuf.ServiceException;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;

public class FeignResponseHandler {

    public static <T> T handleResponse(ApiResponse<T> response, String operation) throws ServiceException {
        if(response == null){
            throw new ServiceException("Null response received for " + operation);
        }
        if(response.getErrno() != 0) {
            throw new ServiceException(
                    String.format("Operation '%s' failed with error: %s (code: %d)",
                            operation, response.getErrmsg(), response.getErrno())
            );
        }

        if (response.getData() == null) {
            throw new ServiceException("No data received for " + operation);
        }

        return response.getData();
    }
}
