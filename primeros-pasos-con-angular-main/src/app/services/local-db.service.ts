import { Injectable } from '@angular/core';

/**
 * Base de datos del cliente. Guarda cada "tabla" como JSON en el localStorage
 * del navegador (con respaldo en memoria si el navegador no lo permite).
 * Cada tabla tiene ids autoincrementales, igual que una base de datos real.
 */
@Injectable({ providedIn: 'root' })
export class LocalDbService {
  private readonly prefijo = 'clinica_db_';
  private memoria = new Map<string, string>();

  private leer(clave: string): string | null {
    try {
      return localStorage.getItem(clave);
    } catch {
      return this.memoria.get(clave) ?? null;
    }
  }

  private escribir(clave: string, valor: string) {
    try {
      localStorage.setItem(clave, valor);
    } catch {
      this.memoria.set(clave, valor);
    }
  }

  listar<T>(tabla: string): T[] {
    const crudo = this.leer(this.prefijo + tabla);
    if (!crudo) return [];
    try {
      return JSON.parse(crudo) as T[];
    } catch {
      return [];
    }
  }

  private guardarTodo<T>(tabla: string, filas: T[]) {
    this.escribir(this.prefijo + tabla, JSON.stringify(filas));
  }

  private siguienteId<T extends { id?: number }>(tabla: string, filas: T[]): number {
    const claveSeq = this.prefijo + tabla + '_seq';
    const maxActual = filas.reduce((m, f) => Math.max(m, f.id ?? 0), 0);
    const guardado = Number(this.leer(claveSeq) ?? 0);
    const id = Math.max(maxActual, guardado) + 1;
    this.escribir(claveSeq, String(id));
    return id;
  }

  obtener<T extends { id?: number }>(tabla: string, id: number): T | undefined {
    return this.listar<T>(tabla).find((f) => f.id === id);
  }

  insertar<T extends { id?: number }>(tabla: string, fila: T): T {
    const filas = this.listar<T>(tabla);
    const nueva = { ...fila, id: this.siguienteId(tabla, filas) };
    filas.push(nueva);
    this.guardarTodo(tabla, filas);
    return nueva;
  }

  actualizar<T extends { id?: number }>(tabla: string, fila: T): T {
    const filas = this.listar<T>(tabla);
    const i = filas.findIndex((f) => f.id === fila.id);
    if (i < 0) throw new Error('El registro no existe');
    filas[i] = { ...filas[i], ...fila };
    this.guardarTodo(tabla, filas);
    return filas[i];
  }

  eliminar<T extends { id?: number }>(tabla: string, id: number) {
    this.guardarTodo(
      tabla,
      this.listar<T>(tabla).filter((f) => f.id !== id)
    );
  }

  /** Inserta datos iniciales solo si la tabla nunca se había creado. */
  sembrar<T extends { id?: number }>(tabla: string, filas: T[]) {
    if (this.leer(this.prefijo + tabla) !== null) return;
    this.guardarTodo(tabla, []);
    filas.forEach((f) => this.insertar(tabla, f));
  }
}
