package com.kilikili.admin.service;

import com.kilikili.admin.dto.AdminCategoryRequest;
import com.kilikili.admin.entity.AdminCategoryItem;
import com.kilikili.admin.mapper.AdminCategoryMapper;
import com.kilikili.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminCategoryService {
    private static final Logger log = LoggerFactory.getLogger(AdminCategoryService.class);
    private static final int MAX_SORT_ORDER = 9999;

    private final AdminCategoryMapper adminCategoryMapper;

    public AdminCategoryService(AdminCategoryMapper adminCategoryMapper) {
        this.adminCategoryMapper = adminCategoryMapper;
    }

    public List<AdminCategoryItem> list() {
        return adminCategoryMapper.list();
    }

    @Transactional
    public AdminCategoryItem create(AdminCategoryRequest request) {
        CategoryValues values = validate(request, null);
        adminCategoryMapper.insert(values.name(), values.code(), values.parentId(), values.sortOrder());
        Long id = adminCategoryMapper.findIdByCode(values.code());
        log.info("管理员新增分类: categoryId={}, name={}, parentId={}", id, values.name(), values.parentId());
        return adminCategoryMapper.findById(id);
    }

    @Transactional
    public AdminCategoryItem update(long id, AdminCategoryRequest request) {
        if (adminCategoryMapper.findById(id) == null) {
            throw new BusinessException("分类不存在");
        }
        CategoryValues values = validate(request, id);
        if (values.parentId() != null && values.parentId() == id) {
            throw new BusinessException("分类不能设置为自己的子分类");
        }
        if (adminCategoryMapper.update(id, values.name(), values.code(), values.parentId(), values.sortOrder()) == 0) {
            throw new BusinessException("分类不存在");
        }
        log.info("管理员更新分类: categoryId={}, name={}, parentId={}", id, values.name(), values.parentId());
        return adminCategoryMapper.findById(id);
    }

    @Transactional
    public void delete(long id) {
        if (adminCategoryMapper.findById(id) == null) {
            throw new BusinessException("分类不存在或已被删除");
        }
        if (adminCategoryMapper.countChildren(id) > 0) {
            throw new BusinessException("请先删除该分类下的子分类");
        }
        if (adminCategoryMapper.countVideos(id) > 0) {
            throw new BusinessException("该分类下仍有关联视频，不能删除");
        }
        adminCategoryMapper.deleteById(id);
        log.info("管理员删除分类: categoryId={}", id);
    }

    private CategoryValues validate(AdminCategoryRequest request, Long excludeId) {
        String name = request.name().trim();
        String code = request.code().trim();
        int sortOrder = request.sortOrder() == null ? 0 : request.sortOrder();
        if (sortOrder < 0 || sortOrder > MAX_SORT_ORDER) {
            throw new BusinessException("排序值必须在0到9999之间");
        }
        if (adminCategoryMapper.countByName(name, excludeId) > 0) {
            throw new BusinessException("分类名称已存在");
        }
        if (adminCategoryMapper.countByCode(code, excludeId) > 0) {
            throw new BusinessException("分类编码已存在");
        }
        Long parentId = request.parentId();
        if (parentId != null) {
            AdminCategoryItem parent = adminCategoryMapper.findById(parentId);
            if (parent == null) {
                throw new BusinessException("父分类不存在");
            }
            if (parent.parentId() != null) {
                throw new BusinessException("目前只支持两级分类");
            }
            if (excludeId != null && adminCategoryMapper.countChildren(excludeId) > 0) {
                throw new BusinessException("包含子分类的分类不能移动到其他分类下");
            }
        }
        return new CategoryValues(name, code, parentId, sortOrder);
    }

    private record CategoryValues(String name, String code, Long parentId, int sortOrder) {
    }
}
