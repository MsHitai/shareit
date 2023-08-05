package ru.practicum.shareit.booking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.practicum.shareit.booking.dto.BookItemDto;
import ru.practicum.shareit.booking.dto.BookerDto;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.model.Booking;
import ru.practicum.shareit.booking.model.BookingState;
import ru.practicum.shareit.booking.model.Status;
import ru.practicum.shareit.booking.repository.BookingRepository;
import ru.practicum.shareit.exception.DataNotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.request.ItemRequest;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingServiceImpl service;

    private User requester;
    private User owner = new User();
    private Booking booking;
    private BookingDto dto;
    private Item item = new Item();
    private long requesterId;
    private long ownerId;
    private final LocalDateTime date =
            LocalDateTime.of(2023, Month.AUGUST, 4, 15, 16, 1);

    @BeforeEach
    void setUp() {
        ownerId = 1L;
        requesterId = 2L;
        BookerDto bookerDto = new BookerDto(2L);

        BookItemDto bookItemDto = new BookItemDto(1L, "Test Item");

        owner = User.builder()
                .id(ownerId)
                .name("TestRob")
                .email("test2@test.ru")
                .items(Set.of(item))
                .build();

        requester = User.builder()
                .id(requesterId)
                .name("TestBob")
                .email("test@test.ru")
                .items(Set.of(new Item()))
                .build();

        item = Item.builder()
                .id(1L)
                .name("Test Item")
                .description("Perfect Test Item Ever")
                .user(owner)
                .available(true)
                .comments(new ArrayList<>())
                .itemRequest(new ItemRequest())
                .build();

        booking = Booking.builder()
                .id(1L)
                .booker(requester)
                .item(item)
                .start(date)
                .end(date.plusHours(2))
                .status(Status.WAITING)
                .build();

        dto = BookingDto.builder()
                .id(1L)
                .itemId(1L)
                .start(date)
                .end(date.plusHours(2))
                .booker(bookerDto)
                .item(bookItemDto)
                .status(Status.WAITING)
                .build();
    }

    @Test
    void testAddBookingOk() {
        when(userRepository.findById(requesterId)).thenReturn(requester);
        when(itemRepository.findById(item.getId())).thenReturn(item);
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        BookingDto actualBooking = service.addBooking(requesterId, dto);

        assertThat(actualBooking.getId(), is(dto.getId()));
        assertThat(actualBooking.getStart(), is(dto.getStart()));
        assertThat(actualBooking.getEnd(), is(dto.getEnd()));
        assertThat(actualBooking.getStatus(), is(dto.getStatus()));
    }

    @Test
    void testAddBookingFailWhenWrongUserId() {
        long wrongId = 22L;
        when(userRepository.findById(wrongId)).thenThrow(new DataNotFoundException());

        assertThrows(DataNotFoundException.class, () -> service.addBooking(wrongId, dto));
    }

    @Test
    void testAddBookingFailWhenWrongItemId() {
        long wrongId = 22L;
        dto.setItemId(wrongId);

        when(userRepository.findById(requesterId)).thenReturn(requester);
        when(itemRepository.findById(wrongId)).thenThrow(new DataNotFoundException());

        assertThrows(DataNotFoundException.class, () -> service.addBooking(requesterId, dto));
    }

    @Test
    void testAddBookingFailWhenItemUnavailable() {
        item.setAvailable(false);
        when(userRepository.findById(requesterId)).thenReturn(requester);
        when(itemRepository.findById(item.getId())).thenReturn(item);

        assertThrows(ValidationException.class, () -> service.addBooking(requesterId, dto));
    }

    @Test
    void testAddBookingFailWhenOwnerTriesToBookHisOwnItem() {
        when(userRepository.findById(ownerId)).thenReturn(owner);
        when(itemRepository.findById(item.getId())).thenReturn(item);

        assertThrows(DataNotFoundException.class, () -> service.addBooking(ownerId, dto));
    }

    @Test
    void testApproveBookingOk() {
        booking.setStatus(Status.REJECTED);
        when(userRepository.findById(ownerId)).thenReturn(owner);
        when(bookingRepository.findById(booking.getId())).thenReturn(booking);
        when(itemRepository.findAllItemsByUser(ownerId)).thenReturn(List.of(item));
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        BookingDto actualBooking = service.approveBooking(ownerId, booking.getId(), false);

        assertThat(actualBooking.getStatus(), is(booking.getStatus()));
    }

    @Test
    void testApproveBookingFailWhenUserIsNotOwner() {
        booking.setStatus(Status.REJECTED);
        when(userRepository.findById(requesterId)).thenReturn(requester);
        when(bookingRepository.findById(booking.getId())).thenReturn(booking);
        when(itemRepository.findAllItemsByUser(requesterId)).thenReturn(new ArrayList<>());
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        assertThrows(DataNotFoundException.class,
                () -> service.approveBooking(requesterId, booking.getId(), false));
    }

    @Test
    void testApproveBookingFailWhenBookingIsAlreadyApproved() {
        booking.setStatus(Status.APPROVED);
        when(userRepository.findById(ownerId)).thenReturn(owner);
        when(bookingRepository.findById(booking.getId())).thenReturn(booking);
        when(itemRepository.findAllItemsByUser(ownerId)).thenReturn(List.of(item));
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);

        assertThrows(ValidationException.class,
                () -> service.approveBooking(ownerId, booking.getId(), false));
    }

    @Test
    void testFindByIdOk() {
        when(userRepository.findById(ownerId)).thenReturn(owner);
        when(bookingRepository.findById(booking.getId())).thenReturn(booking);
        when(itemRepository.findAllItemsByUser(ownerId)).thenReturn(List.of(item));

        BookingDto actualBooking = service.findById(ownerId, booking.getId());

        assertThat(actualBooking.getId(), is(dto.getId()));
        assertThat(actualBooking.getStart(), is(dto.getStart()));
        assertThat(actualBooking.getEnd(), is(dto.getEnd()));
        assertThat(actualBooking.getStatus(), is(dto.getStatus()));
    }

    @Test
    void testFindByIdFailWhenUserIsNotBookerOrOwner() {
        User newUser = User.builder().id(3L).build();
        when(userRepository.findById(newUser.getId())).thenReturn(newUser);
        when(bookingRepository.findById(booking.getId())).thenReturn(booking);
        when(itemRepository.findAllItemsByUser(newUser.getId())).thenReturn(new ArrayList<>());

        assertThrows(DataNotFoundException.class,
                () -> service.findById(newUser.getId(), booking.getId()));
    }

    @Test
    void testFindAllBookingsByUser() {
        Pageable page = PageRequest.of(0, 20);
        when(userRepository.findById(requesterId)).thenReturn(requester);
        when(bookingRepository.findAllByBookerAndEndBeforeOrderByEndDesc(any(User.class), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(List.of(booking));

        List<BookingDto> resultDtos = service.findAllBookingsByUser(requesterId, BookingState.PAST, page);
        BookingDto actualBooking = resultDtos.get(0);

        assertThat(resultDtos.size(), is(1));
        assertThat(actualBooking.getId(), is(dto.getId()));
        assertThat(actualBooking.getStart(), is(dto.getStart()));
        assertThat(actualBooking.getEnd(), is(dto.getEnd()));
        assertThat(actualBooking.getStatus(), is(dto.getStatus()));
    }

    @Test
    void testFindAllBookingsByOwnerOk() {
        Pageable page = PageRequest.of(0, 20);
        when(userRepository.findById(ownerId)).thenReturn(owner);
        when(itemRepository.findAllItemsByUser(ownerId)).thenReturn(List.of(item));
        when(bookingRepository.findAllByItemInAndStatusEqualsOrderByStartDesc(List.of(item), Status.WAITING, page))
                .thenReturn(List.of(booking));

        List<BookingDto> resultDtos = service.findAllBookingsByOwner(ownerId, BookingState.WAITING, page);
        BookingDto actualBooking = resultDtos.get(0);

        assertThat(resultDtos.size(), is(1));
        assertThat(actualBooking.getId(), is(dto.getId()));
        assertThat(actualBooking.getStart(), is(dto.getStart()));
        assertThat(actualBooking.getEnd(), is(dto.getEnd()));
        assertThat(actualBooking.getStatus(), is(dto.getStatus()));
    }
}