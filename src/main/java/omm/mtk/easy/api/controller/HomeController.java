package omm.mtk.easy.api.controller;

import omm.mtk.easy.api.annotation.Autowired;
import omm.mtk.easy.api.annotation.GetMapping;
import omm.mtk.easy.api.annotation.RestController;
import omm.mtk.easy.api.service.IUserService;

/**
 * @author mahatoky rasolonirina
 */
@RestController("/home")
public class HomeController {
    
    @Autowired
    private IUserService userService;
    
    @GetMapping("")
    public String index(){
        return userService.getName();
    }
    
}
