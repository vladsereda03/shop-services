package shop.auth.controller;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import shop.auth.model.dto.UserDTO;
import shop.auth.model.dto.exceptions.RegistrationException;
import shop.auth.model.User;
import shop.auth.service.AccountService;

@Controller
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;
    private final UserDetailsService userDetailsService;
    private final SavedRequestAwareAuthenticationSuccessHandler successHandler;

    @Autowired
    public AccountController(AccountService accountService,
                             UserDetailsService userDetailsService,
                             SavedRequestAwareAuthenticationSuccessHandler successHandler) {
        this.accountService = accountService;
        this.userDetailsService = userDetailsService;
        this.successHandler = successHandler;
    }

    @GetMapping("/login/form")
    public String loginForm(HttpSession httpSession, Model model,
                            @RequestParam(value = "error", required = false) String error) {
        model.addAttribute("userDTO", new UserDTO());
        if (error != null) {
            model.addAttribute("authenticationWarning", "Невірний логін або пароль");
        } else {
            model.addAttribute("authenticationWarning", null);
        }
        return "authentication/log_in";
    }

    @PostMapping("/signup")
    public String signup(HttpServletRequest request,
                         HttpServletResponse response,
                         @ModelAttribute("userDTO") UserDTO userDTO,
                         RedirectAttributes redirectAttributes) throws Exception {
        try {
            User savedUser = accountService.createAndSaveUser(userDTO);

            // Создаём UserDetails и аутентифицируем пользователя
            UserDetails uds = userDetailsService.loadUserByUsername(savedUser.getUsername());
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(uds, null, uds.getAuthorities());

            // Кладём Authentication в SecurityContext
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            // Сохраняем SecurityContext в сессию
            request.getSession().setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    securityContext
            );

            // Запускаем success handler
            successHandler.onAuthenticationSuccess(request, response, authentication);

            return null; // successHandler уже сделал redirect

        } catch (RegistrationException e) {
            // Flash-атрибут для одноразового отображения
            redirectAttributes.addFlashAttribute("registrationError", e.getMessage());
            return "redirect:/account/signup/form";
        }
    }

    @GetMapping("/signup/form")
    public String signupForm(Model model) {
        if (!model.containsAttribute("userDTO")) {
            model.addAttribute("userDTO", new UserDTO());
        }
        return "authentication/sign_up";
    }


    @GetMapping("/current")
    public String showCurrentUser(HttpSession httpSession) {
        // Этот endpoint — fallback после успешного логина если нет saved request
        return "redirect:/goods";
    }

    @GetMapping("/logout")
    public String logout(HttpSession httpSession) {
        httpSession.invalidate();
        return "redirect:/account/login/form";
    }
}