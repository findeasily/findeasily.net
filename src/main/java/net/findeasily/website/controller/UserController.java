package net.findeasily.website.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import javax.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import lombok.extern.slf4j.Slf4j;
import net.findeasily.website.domain.CurrentUser;
import net.findeasily.website.domain.form.UserCreateForm;
import net.findeasily.website.domain.validator.UserCreateFormValidator;
import net.findeasily.website.entity.UserExt;
import net.findeasily.website.service.FileService;
import net.findeasily.website.service.UserService;
import net.findeasily.website.util.ToastrUtils;

@Controller
@Slf4j
public class UserController {

    private final UserService userService;
    private final UserCreateFormValidator userCreateFormValidator;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;

    @Autowired
    public UserController(UserService userService, UserCreateFormValidator userCreateFormValidator,
                          PasswordEncoder passwordEncoder, FileService fileService) {
        this.userService = userService;
        this.userCreateFormValidator = userCreateFormValidator;
        this.passwordEncoder = passwordEncoder;
        this.fileService = fileService;
    }

    @InitBinder("form")
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(userCreateFormValidator);
    }

    @PreAuthorize("@currentUserService.canAccessUser(#user, #id)")
    @RequestMapping("/user/{id}")
    public ModelAndView getUserPage(@PathVariable("id") UUID id, CurrentUser user) {
        log.debug("Getting user page for user={}", id);
        return new ModelAndView("user/user", "user", Optional.ofNullable(userService.getUserById(id.toString()))
                .orElseThrow(() -> new NoSuchElementException(String.format("User=%s not found", id))));
    }

    @GetMapping("/user")
    public ModelAndView getSelfPage(CurrentUser user) {
        Map<String, Object> model = new HashMap<>();
        UserExt userExt = userService.getUserExt(user.getId());
        model.put("user_ext", userExt == null ? new UserExt() : userExt);
        return new ModelAndView("user/user", model);
    }

    @PostMapping("/user")
    public String postUserInfo(CurrentUser user,
                               @RequestParam(value = "file", required = false) MultipartFile file,
                               @RequestParam("self-introduction") String selfIntro) throws IOException {
        if (file != null && !file.isEmpty()) {
            Path saveFile = fileService.storeUserPicture(file, user.getUser());
        }
        userService.updateSelfIntro(selfIntro, user.getId());
        return "redirect:/user";
    }

    @GetMapping("/user/password")
    public ModelAndView getPwsUpdate() {
        return new ModelAndView("user/password");
    }

    @PostMapping("/user/password")
    public ModelAndView postPwsUpdate(CurrentUser user,
                                      @RequestParam("current-password") String currentPassword,
                                      @RequestParam("new-password") String newPassword,
                                      @RequestParam("repeated-password") String repeatedPassword) {
        Map<String, String> model = new HashMap<>();
        if (StringUtils.isAnyBlank(currentPassword, newPassword, repeatedPassword)) {
            model.put(ToastrUtils.KEY, ToastrUtils.error("invalid request: current password, new password and password confirmation must be provided"));
        }
        if (!newPassword.equals(repeatedPassword)) {
            model.put(ToastrUtils.KEY, ToastrUtils.error("new passwords are not matched"));
        }
        if (!passwordEncoder.matches(currentPassword, user.getUser().getPassword())) {
            model.put(ToastrUtils.KEY, ToastrUtils.error("Current password is not matched"));
        }
        if (model.isEmpty() && userService.updatePassword(user.getId(), newPassword)) {
            model.put(ToastrUtils.KEY, ToastrUtils.success("Password is updated successfully"));
        }
        return new ModelAndView("user/password", model);
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/user/create")
    public ModelAndView getUserCreatePage() {
        log.debug("Getting user create form");
        return new ModelAndView("user/user_create", "form", new UserCreateForm());
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/user/create")
    public String handleUserCreateForm(@Valid @ModelAttribute("form") UserCreateForm form, BindingResult bindingResult) {
        log.debug("Processing user create form={}, bindingResult={}", form, bindingResult);
        if (bindingResult.hasErrors()) {
            // failed validation
            return "user/user_create";
        }
        try {
            userService.create(form);
        } catch (DataIntegrityViolationException e) {
            // probably email already exists - very rare case when multiple admins are adding same user
            // at the same time and form validation has passed for more than one of them.
            log.warn("Exception occurred when trying to save the user, assuming duplicate email", e);
            bindingResult.reject("email.exists", "Email already exists");
            return "user/user_create";
        }
        // ok, redirect
        return "redirect:/users";
    }
}
