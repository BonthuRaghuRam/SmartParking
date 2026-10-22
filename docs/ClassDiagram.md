# Class Diagram

Class-level view of the Smart Parking Lot backend, written as a plain-text tree so it renders in any viewer.

## 1. Package and class layout

```
src/main/java/com/airtribe/smartparking/
├── SmartparkingApplication.java        Entry point; @EnableRetry (runs outside the transaction);
│                                       Clock bean; seeds 60 spots (3 floors) on startup
├── controller/
│   └── ParkingController.java          REST routing only: getAvailability(), checkIn(), checkOut()
├── dto/
│   ├── CheckInRequest.java             Input: licensePlate (@NotBlank, normalised in setter), vehicleType (@NotNull)
│   └── TicketResponseDto.java          Output: ticketNumber, licensePlate, vehicleType, floor, spotNumber,
│                                       entryTime, exitTime, fee
├── model/
│   ├── ParkingSpot.java                Entity: floor, spotNumber, spotType, isAvailable, @Version
│   ├── Vehicle.java                    Entity: licensePlate (unique), vehicleType
│   ├── Ticket.java                     Entity: ticketNumber (unique), many-to-one Vehicle, many-to-one ParkingSpot,
│   │                                   activePlate (unique, NULL when closed), entryTime, exitTime,
│   │                                   BigDecimal fee, @Version
│   ├── SpotType.java                   enum: SMALL, COMPACT, LARGE
│   └── VehicleType.java                enum: MOTORCYCLE, CAR, BUS
├── strategy/
│   ├── SpotAssignmentStrategy.java     interface: assignSpot(VehicleType, ParkingSpotRepository)
│   ├── NearestFirstSpotStrategy.java   best-fit spot type with fallback, then lowest floor / spot number
│   ├── FeeCalculationStrategy.java     interface: calculateFee(Ticket, LocalDateTime) -> BigDecimal
│   └── HourlyFeeStrategy.java          max(1, ceil(seconds/3600)) x base rate x multiplier;
│                                       rates injected from application.properties
├── service/
│   ├── ParkingService.java             interface: getAvailability(), checkIn(), checkOut()
│   └── ParkingServiceImpl.java         Orchestration, @Transactional, @Retryable, entity -> DTO mapping
├── repository/
│   ├── ParkingSpotRepository.java      findFirst...OrderByFloorAscSpotNumberAsc (PESSIMISTIC_WRITE, SKIP LOCKED),
│   │                                   countAvailableSpotsByFloorAndType, findAllDistinctFloors
│   ├── TicketRepository.java           findByTicketNumber, existsByActivePlate
│   └── VehicleRepository.java          findByLicensePlate
└── exception/
    ├── TicketNotFoundException.java    thrown when a ticket number does not exist
    └── GlobalExceptionHandler.java     @ControllerAdvice: maps exceptions to HTTP responses
```

## 2. Class details

```
ParkingController
├── uses     ParkingService
├── accepts  CheckInRequest
└── returns  TicketResponseDto (wrapped in a message map)

ParkingService (interface)
└── ParkingServiceImpl
    ├── uses     ParkingSpotRepository, TicketRepository, VehicleRepository
    ├── uses     SpotAssignmentStrategy, FeeCalculationStrategy   (interfaces, constructor-injected)
    ├── uses     java.time.Clock                                  (injected, so time is testable)
    ├── creates  Vehicle (first visit), Ticket
    ├── maps to  TicketResponseDto
    └── throws   TicketNotFoundException, IllegalStateException

SpotAssignmentStrategy (interface)
└── NearestFirstSpotStrategy       queries ParkingSpotRepository

FeeCalculationStrategy (interface)
└── HourlyFeeStrategy              reads Ticket (entry time) and Ticket.vehicle (vehicle type)

Entity relationships
Ticket  ──many-to-one──▶  Vehicle  ──▶  VehicleType
Ticket  ──many-to-one──▶  ParkingSpot  ──▶  SpotType

GlobalExceptionHandler
├── TicketNotFoundException                    -> 404
├── IllegalStateException                      -> 409 (already parked / lot full)
├── IllegalArgumentException                   -> 400
├── ObjectOptimisticLockingFailureException    -> 409 (after retries are exhausted)
├── CannotAcquireLockException                 -> 409 (after retries are exhausted)
├── DataIntegrityViolationException            -> 409 (unique-constraint conflict)
├── HttpMessageNotReadableException            -> 400 (unknown enum values list the allowed ones)
└── MethodArgumentNotValidException            -> 400 (field-level details)
```

## 3. Layer dependencies

```
Controller  ->  Service  ->  Strategy  ->  Repository  ->  Model
    |              |
    |              └──->  DTO (TicketResponseDto)
    └──(errors)──>  GlobalExceptionHandler
```

Dependencies point one way only. The service depends on the strategy interfaces, never on the concrete classes, so a new allocation or pricing algorithm is a new `@Component` with no change to the service. Entities stay internal and only DTOs cross the controller boundary.

## 4. Vehicle to spot compatibility

```
MOTORCYCLE  ->  SMALL  ->  COMPACT  ->  LARGE
CAR         ->  COMPACT  ->  LARGE
BUS         ->  LARGE
```

The first spot type with a free spot wins.

## 5. Transaction and retry layering

```
Caller
  └─▶ Retry proxy      (@Retryable, order = LOWEST_PRECEDENCE - 1, outer)
        └─▶ Transaction proxy   (@Transactional, order = LOWEST_PRECEDENCE, inner)
              └─▶ ParkingServiceImpl.checkIn / checkOut
```

The retry advice wraps the transaction, so a failure raised at commit time (for example an optimistic-lock conflict) is caught by the retry and the whole transaction is run again from the start.