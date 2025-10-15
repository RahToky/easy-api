package omm.mtk.easy.api.service;

import omm.mtk.easy.api.annotation.Service;

/**
 * @author mahatoky rasolonirina
 */
@Service
public class UserService implements IUserService{
    @Override
    public String getName() {
        return "Mahatoky";
    }
}
