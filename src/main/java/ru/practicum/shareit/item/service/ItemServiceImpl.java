package ru.practicum.shareit.item.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.booking.dto.BookerAndItemDto;
import ru.practicum.shareit.booking.mapper.BookingMapper;
import ru.practicum.shareit.booking.model.Booking;
import ru.practicum.shareit.booking.model.Status;
import ru.practicum.shareit.booking.repository.BookingRepository;
import ru.practicum.shareit.exception.DataNotFoundException;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.ItemForUserDto;
import ru.practicum.shareit.item.mapper.ItemMapper;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    @Override
    public ItemDto saveItem(ItemDto itemDto, Long userId) {
        User user = checkUserId(userId);
        Item item = ItemMapper.mapToItem(itemDto, user);
        checkItemIfExists(item);
        return ItemMapper.mapToItemDto(itemRepository.save(item));
    }

    @Override
    public ItemDto partialUpdateItem(Map<String, Object> updates, long itemId, long userId) {
        checkUserId(userId);
        Item item = itemRepository.findById(itemId);
        checkItemIfExists(item);
        if (item.getUser().getId() != userId) {
            throw new DataNotFoundException("У пользователя по id " + userId + " нет такой вещи по id " + item.getId());
        }
        return ItemMapper.mapToItemDto(itemRepository.save(patchItem(updates, item)));
    }

    @Override
    public ItemForUserDto findById(long itemId, long userId) {
        checkUserId(userId);
        Item item = itemRepository.findById(itemId);
        checkItemIfExists(item);
        if (checkIfUserIsOwner(userId, item)) {
            return findByIdForUser(item);
        } else {
            return ItemMapper.mapToItemForUserDto(item, null, null);
        }
    }

    @Override
    public List<ItemDto> findAllItems(Long userId) {
        checkUserId(userId);
        return itemRepository.findAllItemsByUser(userId)
                .stream()
                .map(ItemMapper::mapToItemDto)
                .sorted(Comparator.comparing(ItemDto::getId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ItemDto> searchItems(String text, Long userId) {
        checkUserId(userId);
        return itemRepository.searchItemsByNameOrDescriptionContainingIgnoreCase(text, text)
                .stream()
                .filter(Item::isAvailable)
                .map(ItemMapper::mapToItemDto)
                .collect(Collectors.toList());
    }

    private User checkUserId(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            throw new DataNotFoundException("Пользователя с таким id нет в базе");
        } else {
            return user.get();
        }
    }

    private Boolean checkIfUserIsOwner(long userId, Item item) {
        return item.getUser().getId() == userId;
    }

    private void checkItemIfExists(Item item) {
        if (item == null) {
            throw new DataNotFoundException("Вещи с таким id нет в базе");
        }
    }

    private ItemForUserDto findByIdForUser(Item item) {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> bookings = bookingRepository.findAllByItemAndStatusOrderByEndAsc(item, Status.APPROVED);
        if (bookings == null || bookings.size() == 0) {
            return ItemMapper.mapToItemForUserDto(item, null, null);
        } else if (bookings.size() == 1) {
            Booking booking = bookings.get(0);
            BookerAndItemDto bookerAndItemDto = BookingMapper.mapToBookerAndItemDto(booking);
            return ItemMapper.mapToItemForUserDto(item, bookerAndItemDto, null);
        } else {
            Booking b1 = bookings.get(0); // берем минимальный
            Booking b2 = bookings.get(bookings.size() - 1); // и максимальный

            for (Booking booking : bookings) {
                if (booking.getEnd().isBefore(now)) {
                    if (booking.getEnd().isAfter(b1.getEnd())) { // если он до наст времени, но позже минимального
                        b1 = booking; // это будет наш ласт
                    }
                }
                if (booking.getStart().isAfter(now)) {
                    if (booking.getEnd().isBefore(b2.getEnd())) {
                        b2 = booking; // наш некст
                    }
                }
            }
            BookerAndItemDto last = BookingMapper.mapToBookerAndItemDto(b1);
            BookerAndItemDto next = BookingMapper.mapToBookerAndItemDto(b2);
            return ItemMapper.mapToItemForUserDto(item, last, next);
        }
    }

    private Item patchItem(Map<String, Object> updates, Item itemOld) {
        for (String s : updates.keySet()) {
            switch (s) {
                case "name":
                    itemOld.setName((String) updates.get(s));
                    break;
                case "description":
                    itemOld.setDescription((String) updates.get(s));
                    break;
                case "available":
                    itemOld.setAvailable((Boolean) updates.get(s));
                    break;
            }
        }
        return itemOld;
    }
}
