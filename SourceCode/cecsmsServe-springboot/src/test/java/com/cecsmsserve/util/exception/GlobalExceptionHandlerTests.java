package com.cecsmsserve.util.exception;

import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTests {

    @Test
    void databaseConflictUsesTheSameHttpAndEnvelopeStatus() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "constraint", new SQLException("duplicate"));

        ResponseEntity<CommonResult<Void>> response = handler.dataConflict(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getCode());
    }
}
