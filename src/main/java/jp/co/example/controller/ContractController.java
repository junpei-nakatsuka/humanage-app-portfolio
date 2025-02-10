package jp.co.example.controller;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import jp.co.example.controller.form.ContractForm;
import jp.co.example.entity.Contract;
import jp.co.example.entity.User;
import jp.co.example.exception.InvalidContractException;
import jp.co.example.service.ContractService;
import jp.co.example.service.UserService;

@Controller
public class ContractController {
	
	private static final Logger logger = LoggerFactory.getLogger(ContractController.class);
	
	@Autowired
	private ContractService contractService;
	
	@Autowired
	private UserService userService;
	
	@Autowired ModelMapper modelMapper;
	
	@InitBinder
	public void initBinder(WebDataBinder binder) {
		logger.info("[INFO] ContractController.javaでinitBinderが呼ばれました。binder: {}", binder);
	    binder.registerCustomEditor(LocalDate.class, new PropertyEditorSupport() {
	        @Override
	        public void setAsText(String text) throws IllegalArgumentException {
	            try {
	                if (text == null || text.trim().isEmpty()) {
	                    setValue(null);
	                } else {
	                    setValue(LocalDate.parse(text));
	                }
	            } catch (DateTimeParseException e) {
	                logger.error("[ERROR] 日付の解析に失敗しました。無効な日付フォーマット: {}, ERROR: {}", text, e.getMessage(), e);
	                throw new IllegalArgumentException("無効な日付フォーマット: " + text, e);
	            } catch (Exception e) {
	                logger.error("[ERROR] initBinder メソッドで予期しないエラーが発生しました: {}", e.getMessage(), e);
	                throw new IllegalStateException("予期しないエラーが発生しました", e);
	            }
	        }
	    });
	}
	
	@PostMapping("/contracts/save")
	public String saveContract(@ModelAttribute ContractForm form, Model model, RedirectAttributes redirectAttributes) {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		String callingMethodName = stackTrace.length > 2 ? stackTrace[2].getMethodName() : "Unknown";
		logger.info("[INFO] ContractController.javaのsaveContractが呼ばれました。contractForm: {}, model: {}, redirectAttributes: {}, 呼び出し元: {}", form, model, redirectAttributes, callingMethodName);
		
	    try {
	        Integer userId = form.getUserId();
	        if (userId == null) {
	        	logger.error("[ERROR] userIdはnullにしてはいけません。");
	            throw new InvalidContractException("userIdはnullにしてはいけません。");
	        }
	        
	        User user = userService.getUserById(userId);
	        
	        logger.debug("[DEBUG] 取得したuserId: {}, user: {}", userId, user);
	        
	        logger.debug("[DEBUG] ContractForm ID before mapping: {}", form.getId());

	        // IDが0以下ならnullにする（新規登録のため）
	        if (form.getId() != null && form.getId() <= 0) {
	            logger.debug("[DEBUG] ContractForm ID was <= 0, setting to null");
	            form.setId(null);
	        }
	        
	        //既存の契約が有効期限切れでないかチェック
	        List<Contract> activeContracts = contractService.getActiveContractsByUserId(userId);
	        if (!activeContracts.isEmpty()) {
	            redirectAttributes.addFlashAttribute("errorMessage", "既存の契約が有効期限切れでないため、新しい契約を追加できません。");
	            return "redirect:/contracts/new?userId=" + userId;
	        }
	        
	        Contract contract = modelMapper.map(form, Contract.class);
	        
	        logger.debug("[DEBUG] 取得したactiveContracts: {}, contract: {}", activeContracts, contract);
	        
	        contract.setUser(user);

	        if (contract.getStatus() == null) {
	        	logger.debug("[DEBUG] ステータスがnullなので在職中をセットします。");
	            contract.setStatus("在職中");
	        }

	        if (contract.getContractDate() == null) {
	        	logger.error("[ERROR] 契約日はnullにしてはいけません。");
	            throw new InvalidContractException("契約日はnullにしてはいけません。");
	        }
	        
	        logger.debug("[DEBUG] controllerのsaveContractからserviceのsaveContractを呼びます。contract: {}", contract);
	        contractService.saveContract(contract);

	    } catch (InvalidContractException e) {
	        logger.error("[ERROR] 無効な契約データ: {}", e.getMessage(), e);
	        model.addAttribute("errorMessage", e.getMessage());
	        return "error_page";
	    } catch (DataIntegrityViolationException ex) {
	        logger.error("[ERROR] 契約の保存中にデータの整合性違反が発生しました: {}", ex.getMessage(), ex);
	        model.addAttribute("errorMessage", "データ整合性のエラーが発生しました。");
	        return "error_page";
	    } catch (Exception exx) {
	        logger.error("[ERROR] 契約保存中にエラーが発生しました: {}, スタックトレース: {}, 原因: {}, ローカライズメッセージ: {}", exx.toString(), Arrays.toString(exx.getStackTrace()), exx.getCause() != null ? exx.getCause().toString() : "原因不明", exx.getLocalizedMessage());
	        model.addAttribute("errorMessage", "契約保存中にエラーが発生しました。");
	        return "error_page";
	    }
	    return "redirect:/users/" + form.getUserId() + "/contracts";
	}
	
	@GetMapping("/contracts/new")
    public String showContractForm(@RequestParam("userId") Integer userId, Model model) {
        try {
            model.addAttribute("userId", userId);
            model.addAttribute("contractForm", new ContractForm());
        } catch (Exception e) {
            logger.error("[ERROR] 契約フォームの表示中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "契約フォームの表示中にエラーが発生しました。");
            return "error_page";
        }
        return "contract_form";
    }
	
	@GetMapping("/users/{userId}/contracts")
    public String viewContractManagement(@PathVariable("userId") Integer userId, Model model) {
        try {
            User user = userService.getUserById(userId);
            List<Contract> contracts = contractService.getContractsByUserId(userId);
            
            //契約データを最新順に並べ替え
            contracts.sort((c1, c2) -> c2.getContractDate().compareTo(c1.getContractDate()));
            
            //現在の有効な契約を取得
            Contract activeContract = contractService.getActiveContractByUserId(userId);
            model.addAttribute("activeContractId", activeContract != null ? activeContract.getId() : null);
            
            model.addAttribute("user", user);
            model.addAttribute("contracts", contracts);
        } catch (Exception e) {
            logger.error("[ERROR] 契約情報の取得中にエラーが発生しました: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "契約情報の取得中にエラーが発生しました。");
            return "error_page";
        }
        return "contract_management";
    }
	
	@GetMapping("/contracts/edit/{id}")
	public String showEditContractForm(@PathVariable("id") Integer id, Model model) {
		try {
		    Optional<Contract> optionalContract = contractService.getContractById(id);
		    
		    if (optionalContract.isPresent()) {
		        Contract contract = optionalContract.get();
		        model.addAttribute("contract", contract);
		        return "contract_edit";
		    } else {
		        model.addAttribute("errorMessage", "指定された契約が見つかりません。");
		        return "error_page";
		    }
		} catch (Exception e) {
		    logger.error("[ERROR] 契約編集フォームの表示中にエラーが発生しました: {}", e.getMessage(), e);
		    model.addAttribute("errorMessage", "契約編集フォームの表示中にエラーが発生しました。");
		    return "error_page";
		}
	}
	
	@PostMapping("/contracts/update")
	public String updateContract(@ModelAttribute ContractForm form, Model model, RedirectAttributes redirectAttributes) {
		try {
		    Optional<Contract> optionalContract = contractService.getContractById(form.getId());
		    
		    if (optionalContract.isPresent()) {
		        Contract contract = optionalContract.get();
		        
		        //現在の有効な契約が存在するかどうかをチェック
	            List<Contract> activeContracts = contractService.getActiveContractsByUserId(contract.getUser().getId());
	            if (!activeContracts.isEmpty() && !activeContracts.get(0).getId().equals(contract.getId())) {
	                redirectAttributes.addFlashAttribute("errorMessage", "既存の契約が有効期限切れでないため、更新できません。");
	                return "redirect:/contracts/edit/" + contract.getId();
	            }
		        
		        contract.setExpiryDate(form.getExpiryDate());
		        contract.setType(form.getType());
		        contract.setStatus(form.getStatus() == null ? "在職中" : form.getStatus());
		        
		        logger.debug("[DEBUG] controllerのupdateContractからserviceのsaveContractを呼びます。contract: {}", contract);
		        contractService.saveContract(contract);
		        
		        return "redirect:/users/" + contract.getUser().getId() + "/contracts";
		    } else {
		        model.addAttribute("errorMessage", "指定された契約が見つかりません。");
		        return "error_page";
		    }
		} catch (DataIntegrityViolationException e) {
		    logger.error("[ERROR] updateContractにてデータ整合性のエラーが発生しました: {}", e.getMessage(), e);
		    model.addAttribute("errorMessage", "データ整合性のエラーが発生しました。");
		    return "error_page";
		} catch (Exception ex) {
		    logger.error("[ERROR] updateContractにて契約更新中にエラーが発生しました: {}", ex.getMessage(), ex);
		    model.addAttribute("errorMessage", "契約更新中にエラーが発生しました。");
		    return "error_page";
		}
	}
	
	@PostMapping("/contracts/delete/{id}")
	public ResponseEntity<String> deleteContract(@PathVariable("id") Integer id, @RequestParam("password") String password, HttpSession session) {
		try {
		    User currentUser = (User) session.getAttribute("user");
		    if (currentUser == null) {
		        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("ユーザーがログインしていません。");
		    }
		   
		    if (!userService.isPasswordCorrect(currentUser, password)) {
		        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("パスワードが間違っています。");
		    }

		    contractService.deleteContractById(id);
		    return ResponseEntity.ok("契約が削除されました。");
		} catch (DataIntegrityViolationException e) {
		    logger.error("[ERROR] deleteContractにてデータ整合性のエラーが発生しました: {}", e.getMessage(), e);
		    return ResponseEntity.status(HttpStatus.CONFLICT).body("データ整合性のエラーが発生しました。");
		} catch (Exception ex) {
		    logger.error("[ERROR] deleteContractにて契約削除中にエラーが発生しました: {}", ex.getMessage(), ex);
		    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("契約削除中にエラーが発生しました。");
		}
	}
	
	@ExceptionHandler(InvalidContractException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidContractException(InvalidContractException ex, Model model) {
        logger.error("[ERROR] 無効な契約例外: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error_page";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleDataIntegrityViolationException(DataIntegrityViolationException ex, Model model) {
        logger.error("[ERROR] データ整合性のエラーが発生しました: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "データ整合性のエラーが発生しました。");
        return "error_page";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception ex, Model model) {
        logger.error("[ERROR] 予期しないエラーが発生しました: {}", ex.getMessage(), ex);
        model.addAttribute("errorMessage", "予期しないエラーが発生しました。管理者にお問い合わせください。");
        return "error_page";
    }
}
