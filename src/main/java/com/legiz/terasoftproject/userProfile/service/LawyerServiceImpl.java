package com.legiz.terasoftproject.userProfile.service;

import com.legiz.terasoftproject.payment.domain.model.entity.Subscription;
import com.legiz.terasoftproject.payment.domain.persistence.SubscriptionRepository;
import com.legiz.terasoftproject.security.domain.model.entity.Role;
import com.legiz.terasoftproject.security.domain.model.enumeration.Roles;
import com.legiz.terasoftproject.security.domain.persistence.RoleRepository;
import com.legiz.terasoftproject.security.domain.persistence.UserRepository;
import com.legiz.terasoftproject.security.domain.service.communication.AuthenticateResponse;
import com.legiz.terasoftproject.security.middleware.JwtHandler;
import com.legiz.terasoftproject.shared.exception.ResourceNotFoundException;
import com.legiz.terasoftproject.shared.exception.ResourceValidationException;
import com.legiz.terasoftproject.shared.mapping.EnhancedModelMapper;
import com.legiz.terasoftproject.userProfile.domain.model.entity.Customer;
import com.legiz.terasoftproject.userProfile.domain.model.entity.Lawyer;
import com.legiz.terasoftproject.userProfile.domain.model.enumeration.Specialization;
import com.legiz.terasoftproject.userProfile.domain.persistence.LawyerRepository;
import com.legiz.terasoftproject.userProfile.domain.service.LawyerService;
import com.legiz.terasoftproject.userProfile.domain.service.communication.RegisterCustomerResponse;
import com.legiz.terasoftproject.userProfile.domain.service.communication.RegisterLawyerRequest;
import com.legiz.terasoftproject.userProfile.domain.service.communication.RegisterLawyerResponse;
import com.legiz.terasoftproject.userProfile.resource.CustomerResource;
import com.legiz.terasoftproject.userProfile.resource.LawyerResource;
import com.legiz.terasoftproject.userProfile.resource.LawyerSubscriptionResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class LawyerServiceImpl implements LawyerService {

    private static final Logger logger = LoggerFactory.getLogger(LawyerServiceImpl.class);
    private static final String ENTITY = "Lawyer";
    private LawyerRepository lawyerRepository;
    private SubscriptionRepository subscriptionRepository;
    private final Validator validator;
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder encoder;
    private JwtHandler jwtHandler;
    private EnhancedModelMapper mapper;

    public LawyerServiceImpl(LawyerRepository lawyerRepository, SubscriptionRepository subscriptionRepository, Validator validator, UserRepository userRepository,
                             RoleRepository roleRepository, PasswordEncoder encoder, JwtHandler jwtHandler, EnhancedModelMapper mapper) {
        this.lawyerRepository = lawyerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.validator = validator;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.encoder = encoder;
        this.jwtHandler = jwtHandler;
        this.mapper = mapper;
    }

    @Override
    public List<Lawyer> getAll() {
        return lawyerRepository.findAll();
    }

    @Override
    public Page<Lawyer> getAll(Pageable pageable) {
        return lawyerRepository.findAll(pageable);
    }

    @Override
    public Lawyer getById(Long lawyerId) {
        return lawyerRepository.findById(lawyerId)
                .orElseThrow(()->new ResourceNotFoundException(ENTITY, lawyerId));
    }

    @Override
    public ResponseEntity<?> register(RegisterLawyerRequest request) {

        // Validate User with != Username
        if(userRepository.existsByUsername(request.getUsername())) {
            AuthenticateResponse response = new AuthenticateResponse("Username is already taken.");
            return ResponseEntity.badRequest()
                    .body(response.getMessage());
        }

        // Validate User with != Email
        if(userRepository.existsByEmail(request.getEmail())) {
            AuthenticateResponse response = new AuthenticateResponse("Email is already taken.");
            return ResponseEntity.badRequest()
                    .body(response.getMessage());
        }

        try {
            Set<String> rolesStringSet = request.getRoles();
            Set<Role> roles = new HashSet<>();

            if (rolesStringSet == null) {
                roleRepository.findByName(Roles.ROLE_LAWYER)
                        .map(roles::add)
                        .orElseThrow(() -> new RuntimeException("Role not found."));
            } else {
                rolesStringSet.forEach(roleString ->
                        roleRepository.findByName(Roles.valueOf(roleString))
                                .map(roles::add)
                                .orElseThrow(() -> new RuntimeException("Role not found.")));
            }

            Lawyer alternativeLaw = mapper.map(request, Lawyer.class);

            logger.info("Roles: {}", roles);

            Lawyer lawyer = new Lawyer(
                    request.getUsername(),
                    encoder.encode(request.getPassword()),
                    request.getEmail(),
                    roles,
                    request.getLawyerName(),
                    request.getLawyerLastName(),
                    alternativeLaw.getSpecialization(),
                    request.getPriceLegalAdvice(),
                    request.getPriceCustomLegalCase(),
                    alternativeLaw.getSubscription());

            lawyerRepository.save(lawyer);

            LawyerSubscriptionResource resource = mapper.map(lawyer, LawyerSubscriptionResource.class);
            RegisterLawyerResponse response = new RegisterLawyerResponse(resource);
            return ResponseEntity.ok(response.getResource());

        } catch (Exception e) {
            RegisterCustomerResponse response = new RegisterCustomerResponse(e.getMessage());
            return ResponseEntity.badRequest().body(response.getMessage());
        }
    }

    @Override
    public Lawyer update(Long lawyerId, Lawyer request) {
        Set<ConstraintViolation<Lawyer>> violations = validator.validate(request);

        if (!violations.isEmpty())
            throw new ResourceValidationException(ENTITY, violations);

        //Validate Subscription Id
        if (!subscriptionRepository.existsById(request.getSubscription().getId()))
            throw new ResourceNotFoundException("Subscription", request.getSubscription().getId());
        Subscription subscription = subscriptionRepository.getById(request.getSubscription().getId());

        // Validate Lawyer with != Username
        Lawyer lawyerWithUsername = lawyerRepository.findByUsername(request.getUsername());

        if (lawyerWithUsername != null && lawyerWithUsername.getId() != request.getId())
            throw new ResourceValidationException(ENTITY, "A Lawyer with the same username already exists.");

        // Validate Lawyer with != Email
        Lawyer lawyerWithEmail = lawyerRepository.findByEmail(request.getEmail());

        if (lawyerWithEmail != null && lawyerWithEmail.getEmail() != request.getEmail())
            throw new ResourceValidationException(ENTITY, "A Lawyer with the same email already exists.");

        return lawyerRepository.findById(lawyerId).map(lawyer -> {
            lawyer.setUsername(request.getUsername());
            lawyer.setPassword(request.getPassword());
            lawyer.setLawyerName(request.getLawyerName());
            lawyer.setLawyerLastName(request.getLawyerLastName());
            lawyer.setSubscription(subscription);
            return lawyerRepository.save(lawyer);
        }).orElseThrow(() -> new ResourceNotFoundException(ENTITY, lawyerId));
    }

    @Override
    public ResponseEntity<?> delete(Long lawyerId) {
        return lawyerRepository.findById(lawyerId).map(lawyer -> {
            lawyerRepository.delete(lawyer);
            return ResponseEntity.ok().build();
        }).orElseThrow(()-> new ResourceNotFoundException(ENTITY, lawyerId));
    }
}
