package com.ygq.seckill.exception;


import com.ygq.seckill.result.CodeMsg;
import com.ygq.seckill.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;


import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 *
 * 自定义全局异常拦截器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public Result<?> handleGlobalException(GlobalException e) {
        return Result.error(e.getCodeMsg());
    }

    @ExceptionHandler(value = Exception.class) //拦截所有异常
    public Result<String> exceptionHandler(HttpServletRequest request, Exception e){
        e.printStackTrace();
        if(e instanceof GlobalException) {
            GlobalException ex = (GlobalException)e;
            return Result.error(ex.getCodeMsg());
        }else if(e instanceof BindException) {
            BindException ex = (BindException)e;
            List<ObjectError> errors = ex.getAllErrors(); //绑定错误返回很多错误，是一个错误列表，只需要第一个错误
            ObjectError error = errors.get(0);
            String msg = error.getDefaultMessage();
            return Result.error(CodeMsg.BIND_ERROR.fillArgs(msg)); //给状态码填充参数
        }else {
            return Result.error(CodeMsg.SERVER_ERROR);
        }
    }

    @ExceptionHandler(NumberFormatException.class)
    public Result<String> handleNumberFormatException(NumberFormatException e) {
        return Result.error(CodeMsg.MOBILE_ERROR.fillArgs("手机号必须为数字"));
    }
}
