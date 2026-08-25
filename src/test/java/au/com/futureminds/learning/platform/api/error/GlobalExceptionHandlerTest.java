package au.com.futureminds.learning.platform.api.error;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesUnexpectedExceptionAsInternalServerErrorProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new RuntimeException("boom"), mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getTitle()).isEqualTo("Internal Server Error");
    }

    @Test
    void handlesValidationFailureWithFieldErrors() {
        MethodParameter methodParameter = mock(MethodParameter.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("target", "someField", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo("Validation Failed");
        assertThat(body.getProperties()).containsKey("errors");
    }

    @Test
    void handlesMalformedRequestBody() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad json", mock(HttpInputMessage.class));

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo("Malformed Request Body");
    }
}
