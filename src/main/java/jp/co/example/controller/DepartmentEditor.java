package jp.co.example.controller;

import java.beans.PropertyEditorSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.example.entity.Department;
import jp.co.example.exception.InvalidDepartmentException;
import jp.co.example.service.DepartmentService;

public class DepartmentEditor extends PropertyEditorSupport {
	
	private static final Logger logger = LoggerFactory.getLogger(DepartmentEditor.class);

    private final DepartmentService departmentService;

    public DepartmentEditor(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		logger.info("[INFO] DepartmentEditor.javaでsetAsTextが呼ばれました。text: {}", text);
		try {
			if (text == null || text.trim().isEmpty()) {
				logger.info("[INFO] nullをセットします。");
				setValue(null);
			} else {
				Department department = departmentService.findByDepartmentName(text);
				if (department == null) {
					logger.error("[ERROR] 指定された部門が見つかりません: {}", text);
					throw new InvalidDepartmentException("指定された部門が見つかりません: " + text);
				}
				this.setValue(department);
			}
		} catch (Exception e) {
			logger.error("[ERROR] DepartmentEditorでエラーが発生しました: {}", e.getMessage(), e);
			throw new InvalidDepartmentException("無効な部門名が指定されました: " + text, e);
		}
	}
}
