package co.edu.uniquindio.auth_service.exception;

import co.edu.uniquindio.auth_service.dtos.ResponseDTO;
import co.edu.uniquindio.auth_service.dtos.ValidationDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class RestExceptionHandler {

    // captura las excepciones generales y les da formato de respuesta estandar
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDTO<String>> generalExceptionHandler(Exception e) {
        return ResponseEntity.internalServerError().body(new ResponseDTO<>(true, e.getMessage()));
    }

    // captura todas las excepciones lanzadas por las validaciones de jakarta de los
    // dto y las mete en una lista para mostrarlas todas con formato correcto
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseDTO<List<ValidationDTO>>> validationExceptionHandler(MethodArgumentNotValidException ex) {
        List<ValidationDTO> errors = new ArrayList<>();
        BindingResult results = ex.getBindingResult();
        for (FieldError e : results.getFieldErrors()) {
            errors.add(new ValidationDTO(e.getField(), e.getDefaultMessage()));
        }
        return ResponseEntity.badRequest().body(new ResponseDTO<>(true, errors));
    }

    // http 409
    @ExceptionHandler(ValueConflictException.class)
    public ResponseEntity<ResponseDTO<String>> handleValueConflictException(ValueConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ResponseDTO<>(true, ex.getMessage()));
    }

    // http 404
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ResponseDTO<String>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ResponseDTO<>(true, ex.getMessage()));
    }

    // http 400
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ResponseDTO<String>> handleBadRequestException(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ResponseDTO<>(true, ex.getMessage()));
    }

    // http 401
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ResponseDTO<String>> handleUnauthorizedException(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ResponseDTO<>(true, ex.getMessage()));
    }

    // http 403
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ResponseDTO<String>> handleForbiddenException(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ResponseDTO<>(true, ex.getMessage()));
    }
}
