export type EstadoCita = 'PROGRAMADA' | 'ATENDIDA' | 'CANCELADA';

export class Cita {
  id?: number;
  mascotaId?: number;
  medicoId?: number;
  fecha?: string; // yyyy-MM-dd
  hora?: string; // HH:mm
  motivo?: string;
  estado?: EstadoCita;
}
