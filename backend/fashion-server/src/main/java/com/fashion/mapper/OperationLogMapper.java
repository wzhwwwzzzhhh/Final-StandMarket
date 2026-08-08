package com.fashion.mapper;

import com.fashion.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OperationLogMapper {

    int insert(OperationLog operationLog);

    List<OperationLog> selectPage(@Param("module") String module, @Param("keyword") String keyword);
}
