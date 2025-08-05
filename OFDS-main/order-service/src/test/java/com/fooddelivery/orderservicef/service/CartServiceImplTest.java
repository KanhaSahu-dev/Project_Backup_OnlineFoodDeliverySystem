package com.fooddelivery.orderservicef.service;

import com.fooddelivery.orderservicef.dto.CartDTO;
import com.fooddelivery.orderservicef.dto.CartItemDTO;
import com.fooddelivery.orderservicef.dto.MenuItemDTO;
import com.fooddelivery.orderservicef.exception.InvalidOperationException;
import com.fooddelivery.orderservicef.exception.ResourceNotFoundException;
import com.fooddelivery.orderservicef.model.Cart;
import com.fooddelivery.orderservicef.model.CartItem;
import com.fooddelivery.orderservicef.repository.CartItemRepository;
import com.fooddelivery.orderservicef.repository.CartRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartServiceImplTest {

    @InjectMocks
    private CartServiceImpl cartService;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private MenuServiceClient menuServiceClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetOrCreateCart_CartExists() {
        Long userId = 123L;
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(userId);
        cart.setRestaurantId(5965392056977236797L);
        cart.setItems(new ArrayList<>());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        CartDTO result = cartService.getOrCreateCart(userId);

        assertEquals(userId, result.getUserId());
        assertEquals(cart.getId(), result.getId());
        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void testGetOrCreateCart_CartDoesNotExist() {
        Long userId = 123L;
        Cart newCart = new Cart();
        newCart.setId(1L);
        newCart.setUserId(userId);
        newCart.setRestaurantId(5965392056977236797L);
        newCart.setItems(new ArrayList<>());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(newCart);

        CartDTO result = cartService.getOrCreateCart(userId);

        assertEquals(userId, result.getUserId());
        assertEquals(newCart.getId(), result.getId());
        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void testAddItemToCart_InvalidQuantity() {
        Long userId = 123L;
        CartItemDTO itemDTO = new CartItemDTO();
        itemDTO.setMenuItemId(1L);
        itemDTO.setItemName("Pizza");
        itemDTO.setQuantity(0);
        itemDTO.setPrice(BigDecimal.TEN);

        assertThrows(InvalidOperationException.class, () -> cartService.addItemToCart(userId, itemDTO));
        verify(cartRepository, never()).findByUserId(any());
    }

    @Test
    void testAddItemToCart_NullDTO() {
        Long userId = 123L;
        assertThrows(NullPointerException.class, () -> cartService.addItemToCart(userId, null));
    }

    @Test
    void testAddItemToCart_Success_NewCart() {
        Long userId = 123L;
        Long menuItemId = 1L;
        
        CartItemDTO itemDTO = new CartItemDTO();
        itemDTO.setMenuItemId(menuItemId);
        itemDTO.setItemName("Pizza");
        itemDTO.setQuantity(2);
        itemDTO.setPrice(BigDecimal.valueOf(15.50));

        MenuItemDTO menuItemDTO = new MenuItemDTO();
        menuItemDTO.setItemName("Pizza");
        menuItemDTO.setPrice(15.50);
        menuItemDTO.setIsAvailable(true);

        Cart newCart = new Cart();
        newCart.setId(1L);
        newCart.setUserId(userId);
        newCart.setRestaurantId(5965392056977236797L);
        newCart.setItems(new ArrayList<>());

        Cart savedCart = new Cart();
        savedCart.setId(1L);
        savedCart.setUserId(userId);
        savedCart.setRestaurantId(5965392056977236797L);
        savedCart.setItems(new ArrayList<>());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenReturn(newCart, savedCart);
        when(menuServiceClient.getMenuItemById(menuItemId)).thenReturn(menuItemDTO);
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(i -> i.getArgument(0));

        CartDTO result = cartService.addItemToCart(userId, itemDTO);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        verify(cartRepository, times(2)).save(any(Cart.class));
        verify(menuServiceClient, times(1)).getMenuItemById(menuItemId);
        verify(cartItemRepository, times(1)).save(any(CartItem.class));
    }

    @Test
    void testAddItemToCart_Success_ExistingCart() {
        Long userId = 123L;
        Long menuItemId = 1L;
        
        CartItemDTO itemDTO = new CartItemDTO();
        itemDTO.setMenuItemId(menuItemId);
        itemDTO.setItemName("Pizza");
        itemDTO.setQuantity(2);
        itemDTO.setPrice(BigDecimal.valueOf(15.50));

        MenuItemDTO menuItemDTO = new MenuItemDTO();
        menuItemDTO.setItemName("Pizza");
        menuItemDTO.setPrice(15.50);
        menuItemDTO.setIsAvailable(true);

        Cart existingCart = new Cart();
        existingCart.setId(1L);
        existingCart.setUserId(userId);
        existingCart.setRestaurantId(5965392056977236797L);
        existingCart.setItems(new ArrayList<>());

        Cart savedCart = new Cart();
        savedCart.setId(1L);
        savedCart.setUserId(userId);
        savedCart.setRestaurantId(5965392056977236797L);
        savedCart.setItems(new ArrayList<>());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existingCart));
        when(cartRepository.save(any(Cart.class))).thenReturn(savedCart);
        when(menuServiceClient.getMenuItemById(menuItemId)).thenReturn(menuItemDTO);
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(i -> i.getArgument(0));

        CartDTO result = cartService.addItemToCart(userId, itemDTO);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        verify(cartRepository, times(1)).save(any(Cart.class));
        verify(menuServiceClient, times(1)).getMenuItemById(menuItemId);
        verify(cartItemRepository, times(1)).save(any(CartItem.class));
    }

    @Test
    void testAddItemToCart_MenuItemNotFound() {
        Long userId = 123L;
        Long menuItemId = 1L;
        
        CartItemDTO itemDTO = new CartItemDTO();
        itemDTO.setMenuItemId(menuItemId);
        itemDTO.setItemName("Pizza");
        itemDTO.setQuantity(2);
        itemDTO.setPrice(BigDecimal.valueOf(15.50));

        Cart existingCart = new Cart();
        existingCart.setId(1L);
        existingCart.setUserId(userId);
        existingCart.setRestaurantId(5965392056977236797L);
        existingCart.setItems(new ArrayList<>());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(existingCart));
        when(menuServiceClient.getMenuItemById(menuItemId)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> cartService.addItemToCart(userId, itemDTO));
        verify(menuServiceClient, times(1)).getMenuItemById(menuItemId);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void testUpdateCartItem_Success() {
        Long userId = 123L;
        Long itemId = 1L;
        
        CartItemDTO itemDTO = new CartItemDTO();
        itemDTO.setMenuItemId(1L);
        itemDTO.setItemName("Pizza");
        itemDTO.setQuantity(3);
        itemDTO.setPrice(BigDecimal.valueOf(15.50));

        CartItem cartItem = new CartItem();
        cartItem.setId(itemId);
        cartItem.setMenuItemId(1L);
        cartItem.setItemName("Pizza");
        cartItem.setQuantity(2);
        cartItem.setPrice(BigDecimal.valueOf(15.50));

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(userId);
        cart.setRestaurantId(5965392056977236797L);
        cart.setItems(Arrays.asList(cartItem));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(i -> i.getArgument(0));

        CartDTO result = cartService.updateCartItem(userId, itemId, itemDTO);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartItemRepository, times(1)).save(any(CartItem.class));
    }

    @Test
    void testUpdateCartItem_InvalidQuantity() {
        Long userId = 123L;
        Long itemId = 1L;
        
        CartItemDTO itemDTO = new CartItemDTO();
        itemDTO.setMenuItemId(1L);
        itemDTO.setItemName("Pizza");
        itemDTO.setQuantity(0);
        itemDTO.setPrice(BigDecimal.valueOf(15.50));

        assertThrows(InvalidOperationException.class, () -> cartService.updateCartItem(userId, itemId, itemDTO));
        verify(cartRepository, never()).findByUserId(any());
    }

    @Test
    void testUpdateCartItem_CartNotFound() {
        Long userId = 123L;
        Long itemId = 1L;
        
        CartItemDTO itemDTO = new CartItemDTO();
        itemDTO.setMenuItemId(1L);
        itemDTO.setItemName("Pizza");
        itemDTO.setQuantity(3);
        itemDTO.setPrice(BigDecimal.valueOf(15.50));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.updateCartItem(userId, itemId, itemDTO));
        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void testUpdateCartItem_ItemNotFound() {
        Long userId = 123L;
        Long itemId = 999L;
        
        CartItemDTO itemDTO = new CartItemDTO();
        itemDTO.setMenuItemId(1L);
        itemDTO.setItemName("Pizza");
        itemDTO.setQuantity(3);
        itemDTO.setPrice(BigDecimal.valueOf(15.50));

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(userId);
        cart.setRestaurantId(5965392056977236797L);
        cart.setItems(new ArrayList<>());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        assertThrows(ResourceNotFoundException.class, () -> cartService.updateCartItem(userId, itemId, itemDTO));
        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void testRemoveItemFromCart_Success() {
        Long userId = 123L;
        Long itemId = 1L;
        
        CartItem cartItem = new CartItem();
        cartItem.setId(itemId);
        cartItem.setMenuItemId(1L);
        cartItem.setItemName("Pizza");
        cartItem.setQuantity(2);
        cartItem.setPrice(BigDecimal.valueOf(15.50));

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(userId);
        cart.setRestaurantId(5965392056977236797L);
        cart.setItems(new ArrayList<>(Arrays.asList(cartItem)));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        doNothing().when(cartItemRepository).delete(cartItem);
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        cartService.removeItemFromCart(userId, itemId);

        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartItemRepository, times(1)).delete(cartItem);
        verify(cartRepository, times(1)).save(cart);
        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void testRemoveItemFromCart_CartNotFound() {
        Long userId = 123L;
        Long itemId = 1L;

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.removeItemFromCart(userId, itemId));
        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void testRemoveItemFromCart_ItemNotFound() {
        Long userId = 123L;
        Long itemId = 999L;

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(userId);
        cart.setRestaurantId(5965392056977236797L);
        cart.setItems(new ArrayList<>());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        assertThrows(ResourceNotFoundException.class, () -> cartService.removeItemFromCart(userId, itemId));
        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void testClearCart_Success() {
        Long userId = 123L;
        
        CartItem cartItem = new CartItem();
        cartItem.setId(1L);
        cartItem.setMenuItemId(1L);
        cartItem.setItemName("Pizza");
        cartItem.setQuantity(2);
        cartItem.setPrice(BigDecimal.valueOf(15.50));

        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(userId);
        cart.setRestaurantId(5965392056977236797L);
        cart.setItems(new ArrayList<>(Arrays.asList(cartItem)));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        doNothing().when(cartItemRepository).deleteAll(cart.getItems());
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        cartService.clearCart(userId);

        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartItemRepository, times(1)).deleteAll(cart.getItems());
        verify(cartRepository, times(1)).save(cart);
        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void testClearCart_CartNotFound() {
        Long userId = 123L;

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.clearCart(userId));
        verify(cartRepository, times(1)).findByUserId(userId);
        verify(cartItemRepository, never()).deleteAll(any());
    }
} 
