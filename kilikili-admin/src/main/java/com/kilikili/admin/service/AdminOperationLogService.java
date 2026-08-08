package com.kilikili.admin.service;

import com.kilikili.admin.entity.AdminOperationLogItem;
import com.kilikili.admin.entity.AdminOperationLogPage;
import com.kilikili.admin.mapper.AdminOperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminOperationLogService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminOperationLogMapper adminOperationLogMapper;

    public AdminOperationLogService(AdminOperationLogMapper adminOperationLogMapper) {
        this.adminOperationLogMapper = adminOperationLogMapper;
    }

    public AdminOperationLogPage list(String operator, String module, int page, int pageSize) {
        String normalizedOperator = operator == null ? null : operator.trim();
        String normalizedModule = module == null ? null : module.trim();
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long offset = (long) (normalizedPage - 1) * normalizedPageSize;
        long total = adminOperationLogMapper.count(normalizedOperator, normalizedModule);
        List<AdminOperationLogItem> items = offset >= total
                ? List.of()
                : adminOperationLogMapper.list(normalizedOperator, normalizedModule, offset, normalizedPageSize);
        return new AdminOperationLogPage(items, total, normalizedPage, normalizedPageSize, offset + items.size() < total);
    }

    @Transactional
    public int clear() {
        return adminOperationLogMapper.deleteAll();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long operatorId,
                       String operatorName,
                       String module,
                       String action,
                       String description,
                       String ipAddress,
                       String status,
                       String errorMessage,
                       long durationMs) {
        adminOperationLogMapper.insert(operatorId, operatorName, module, action, description,
                ipAddress, status, errorMessage, durationMs);
    }
}
