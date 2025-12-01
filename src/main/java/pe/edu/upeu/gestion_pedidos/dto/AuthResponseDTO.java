package pe.edu.upeu.gestion_pedidos.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class AuthResponseDTO {
    private String token;
    private String userId;
    private String email;
    private String fullName;
}
