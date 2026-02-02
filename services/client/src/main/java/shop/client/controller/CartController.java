package shop.client.controller;

import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import java.security.Principal;

@Controller
@AllArgsConstructor
@RequestMapping("/cart")
public class CartController {
    private final RestClient restClient;

    /*@Autowired
    public CartController(GoodRepository goodRepository, UserRepository userRepository, OrderGenerator orderGenerator, CartService cartService) {
        this.goodRepository = goodRepository;
        this.userRepository = userRepository;
        this.orderGenerator = orderGenerator;
        this.cartService = cartService;
    }*/

    @GetMapping("/add")
    public String addToCart(Principal principal, @RequestParam("quantity") int wishedQuantity) {
        /*String username = principal.getName();

        UserDTO userDto = restClient.get()
                .uri("http://auth.local:9000/connect/userinfo", username)
                .retrieve()
                .body(UserDTO.class);



        User currentUser = userRepository.findUserById(userSession.getId());
        Long itemId = (Long) httpSession.getAttribute("openedItemId");
        Cart cart = currentUser.getCart();
        Good wishedGood = goodRepository.getReferenceById(itemId);
        wishedGood.setQuantity(wishedGood.getQuantity() - wishedQuantity);
        goodRepository.saveAndFlush(wishedGood);
        cart.getItems().merge(wishedGood, wishedQuantity, Integer::sum);
        userRepository.saveAndFlush(currentUser);
        return "redirect:/goods";*/

        return null;
    }

    @GetMapping()
    public String showCart(HttpSession httpSession, Model model) {



        /*User userSession = (User) httpSession.getAttribute("currentUser");
        User currentUser = userRepository.findUserById(userSession.getId());
        Cart cart = currentUser.getCart();
        double price = cart.calculatePrice();

        model.addAttribute("cart", cart);
        model.addAttribute("price", price);
        model.addAttribute("liqpayForm", orderGenerator.createPaymentFormHtml(httpSession, price));

        return "orders/cart";*/

        return null;
    }

    @GetMapping("/clear")
    public String clearCart(HttpSession httpSession) {
        /*User userSession = (User) httpSession.getAttribute("currentUser");
        User currentUser = userRepository.findUserById(userSession.getId());
        cartService.clearCart(currentUser.getCart());
        userRepository.saveAndFlush(currentUser);
        return "redirect:/goods";*/

        return null;
    }


    //todo: shift logic to order microservice (OrderGenerator etc.)
    public String createPaymentFormHtml(HttpSession httpSession, double uah) {
        /*Map<String, String> params = new HashMap<>();
        params.put("action", "pay");
        params.put("amount", String.valueOf(uah));
        params.put("currency", "UAH");
        params.put("description", "Оплата обраних товарів");
        params.put("order_id", Long.toString(cartRepository.getCurrentOrderSeq()-1));
        params.put("version", "3");
        params.put("sandbox", "1");
        params.put("result_url", "http://electropoint.hopto.org/order/new");
        params.put("server_url", "http://electropoint.hopto.org/order/payment");
        params.put("info", "no info");
        LiqPay liqpay = new LiqPay(PUBLIC_KEY, PRIVATE_KEY);
        System.out.println("--------------------------");
        System.out.println(PUBLIC_KEY);
        System.out.println(PRIVATE_KEY);
        System.out.println("--------------------------");
        return liqpay.cnb_form(params);*/

        return null;
    }
}
