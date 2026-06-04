import struct
import socket
import threading
import time
import random
import math
import os
import json

STAGE_BASE_ADDRESSES = {
    'raw_water': 0,
    'flocculation': 20,
    'sedimentation': 40,
    'filtration': 60,
    'outlet': 80,
}

REGISTER_DATA = {}

DEFAULT_PARAMS = {
    'raw_water': {
        'turbidity': 18.0, 'ph': 7.1, 'temperature': 18.0,
        'ammonia': 0.12, 'cod': 2.5, 'flow_rate': 8333.0,
        'coagulant_dose': 5.0, 'chlorine_dose': 2.0
    },
    'flocculation': {
        'turbidity_ratio': 0.5, 'ph_offset': -0.1, 'temp_offset': -0.1,
        'ammonia_ratio': 0.6, 'cod_ratio': 0.5
    },
    'sedimentation': {
        'turbidity_ratio': 0.12, 'ph_offset': 0.0, 'temp_offset': -0.2,
        'ammonia_ratio': 0.2, 'cod_ratio': 0.25
    },
    'filtration': {
        'turbidity_ratio': 0.02, 'ph_offset': 0.0, 'temp_offset': -0.3,
        'ammonia_ratio': 0.08, 'cod_ratio': 0.1
    },
    'outlet': {
        'turbidity_ratio': 0.015, 'ph_offset': 0.0, 'temp_offset': -0.3,
        'ammonia_ratio': 0.05, 'cod_ratio': 0.07
    }
}

DEFAULT_FLUCTUATION = {
    'turbidity': 2.0, 'ph': 0.1, 'temperature': 0.5,
    'ammonia': 0.02, 'cod': 0.3, 'flow_rate': 200.0,
    'coagulant_dose': 0.3, 'chlorine_dose': 0.2,
    'turbidity_daily_amplitude': 5.0,
    'temperature_daily_amplitude': 2.0
}

params = {}
fluctuation = {}
update_interval = int(os.environ.get('UPDATE_INTERVAL', '300'))
listen_host = os.environ.get('LISTEN_HOST', '0.0.0.0')
listen_port = int(os.environ.get('LISTEN_PORT', '502'))

def load_config():
    global params, fluctuation

    params = json.loads(os.environ.get('WATER_PARAMS', json.dumps(DEFAULT_PARAMS)))
    fluctuation = json.loads(os.environ.get('WATER_FLUCTUATION', json.dumps(DEFAULT_FLUCTUATION)))

    custom_turbidity = os.environ.get('RAW_TURBIDITY')
    custom_ph = os.environ.get('RAW_PH')
    custom_temp = os.environ.get('RAW_TEMPERATURE')
    custom_flow = os.environ.get('RAW_FLOW_RATE')

    if 'raw_water' not in params:
        params['raw_water'] = {}
    if custom_turbidity: params['raw_water']['turbidity'] = float(custom_turbidity)
    if custom_ph: params['raw_water']['ph'] = float(custom_ph)
    if custom_temp: params['raw_water']['temperature'] = float(custom_temp)
    if custom_flow: params['raw_water']['flow_rate'] = float(custom_flow)

    custom_floc_ratio = os.environ.get('FLOCCULATION_TURB_RATIO')
    if custom_floc_ratio:
        if 'flocculation' not in params: params['flocculation'] = {}
        params['flocculation']['turbidity_ratio'] = float(custom_floc_ratio)

    custom_sed_ratio = os.environ.get('SEDIMENTATION_TURB_RATIO')
    if custom_sed_ratio:
        if 'sedimentation' not in params: params['sedimentation'] = {}
        params['sedimentation']['turbidity_ratio'] = float(custom_sed_ratio)

    custom_filt_ratio = os.environ.get('FILTRATION_TURB_RATIO')
    if custom_filt_ratio:
        if 'filtration' not in params: params['filtration'] = {}
        params['filtration']['turbidity_ratio'] = float(custom_filt_ratio)

    custom_outlet_ratio = os.environ.get('OUTLET_TURB_RATIO')
    if custom_outlet_ratio:
        if 'outlet' not in params: params['outlet'] = {}
        params['outlet']['turbidity_ratio'] = float(custom_outlet_ratio)

    f_turb = os.environ.get('FLUCT_TURBIDITY')
    f_ph = os.environ.get('FLUCT_PH')
    f_temp = os.environ.get('FLUCT_TEMPERATURE')
    f_flow = os.environ.get('FLUCT_FLOW_RATE')

    if f_turb: fluctuation['turbidity'] = float(f_turb)
    if f_ph: fluctuation['ph'] = float(f_ph)
    if f_temp: fluctuation['temperature'] = float(f_temp)
    if f_flow: fluctuation['flow_rate'] = float(f_flow)

def float_to_registers(value):
    return struct.pack('>f', value)

def g(sigma):
    return random.gauss(0, sigma)

def generate_data():
    rw = params.get('raw_water', DEFAULT_PARAMS['raw_water'])
    fl = fluctuation

    hour = time.localtime().tm_hour
    day_sin = math.sin(hour / 24 * 2 * math.pi)

    raw_turb = max(1, rw.get('turbidity', 18.0) + fl.get('turbidity_daily_amplitude', 5.0) * day_sin + g(fl.get('turbidity', 2.0)))
    raw_ph = max(6.0, min(9.0, rw.get('ph', 7.1) + g(fl.get('ph', 0.1))))
    raw_temp = max(2, rw.get('temperature', 18.0) + fl.get('temperature_daily_amplitude', 2.0) * day_sin + g(fl.get('temperature', 0.5)))
    raw_ammonia = max(0.01, rw.get('ammonia', 0.12) + g(fl.get('ammonia', 0.02)))
    raw_cod = max(0.3, rw.get('cod', 2.5) + g(fl.get('cod', 0.3)))
    flow_rate = max(4000, rw.get('flow_rate', 8333.0) + g(fl.get('flow_rate', 200.0)))
    coagulant_dose = 2.5 + raw_turb * 0.15 + flow_rate * 0.001 + g(fl.get('coagulant_dose', 0.3))
    chlorine_dose = 1.5 + g(fl.get('chlorine_dose', 0.2))

    REGISTER_DATA[0] = [
        float_to_registers(raw_turb), float_to_registers(raw_ph),
        float_to_registers(raw_temp), float_to_registers(raw_ammonia),
        float_to_registers(raw_cod), float_to_registers(flow_rate),
        float_to_registers(coagulant_dose), float_to_registers(chlorine_dose),
    ]

    stage_configs = [
        ('flocculation', 20, 0.5, -0.1, -0.1, 0.6, 0.5, 1.0),
        ('sedimentation', 40, 0.12, 0.0, -0.2, 0.2, 0.25, 0.3),
        ('filtration', 60, 0.02, 0.0, -0.3, 0.08, 0.1, 0.05),
        ('outlet', 80, 0.015, 0.0, -0.3, 0.05, 0.07, 0.02),
    ]

    for stage_name, base_addr, def_turb_r, def_ph_o, def_temp_o, def_nh3_r, def_cod_r, noise_scale in stage_configs:
        sc = params.get(stage_name, DEFAULT_PARAMS.get(stage_name, {}))
        turb_r = sc.get('turbidity_ratio', def_turb_r)
        ph_o = sc.get('ph_offset', def_ph_o)
        temp_o = sc.get('temp_offset', def_temp_o)
        nh3_r = sc.get('ammonia_ratio', def_nh3_r)
        cod_r = sc.get('cod_ratio', def_cod_r)

        s_turb = raw_turb * turb_r + g(noise_scale)
        s_ph = raw_ph + ph_o + g(0.05)
        s_temp = raw_temp + temp_o
        s_nh3 = raw_ammonia * nh3_r
        s_cod = raw_cod * cod_r

        REGISTER_DATA[base_addr] = [
            float_to_registers(s_turb), float_to_registers(s_ph),
            float_to_registers(s_temp), float_to_registers(s_nh3),
            float_to_registers(s_cod), float_to_registers(flow_rate),
            float_to_registers(0), float_to_registers(0),
        ]

    print(f"[{time.strftime('%H:%M:%S')}] 数据更新 | 浊度={raw_turb:.1f} pH={raw_ph:.2f} "
          f"流量={flow_rate:.0f} 投加量={coagulant_dose:.2f}mg/L", flush=True)

def handle_client(conn, addr):
    try:
        while True:
            header = conn.recv(7)
            if len(header) < 7:
                break
            trans_id = struct.unpack('>H', header[0:2])[0]
            proto_id = struct.unpack('>H', header[2:4])[0]
            length = struct.unpack('>H', header[4:6])[0]
            unit_id = header[6]

            remaining = length - 1
            body = conn.recv(remaining) if remaining > 0 else b''
            if len(body) < 4:
                break

            func_code = body[0]
            start_addr = struct.unpack('>H', body[1:3])[0]
            quantity = struct.unpack('>H', body[3:5])[0]

            if func_code == 0x03:
                base_addr = (start_addr // 20) * 20
                reg_offset = start_addr - base_addr
                reg_list = REGISTER_DATA.get(base_addr, [])
                byte_count = quantity * 2
                response_data = bytearray()

                for i in range(quantity):
                    abs_reg = reg_offset + i
                    reg_index = abs_reg // 2
                    if reg_index < len(reg_list) and abs_reg % 2 == 0:
                        response_data.extend(reg_list[reg_index][0:2])
                    elif reg_index < len(reg_list) and abs_reg % 2 == 1:
                        response_data.extend(reg_list[reg_index][2:4])
                    else:
                        response_data.extend(b'\x00\x00')

                resp = struct.pack('>H', trans_id) + struct.pack('>H', proto_id)
                resp += struct.pack('>H', 1 + 1 + 1 + len(response_data))
                resp += struct.pack('B', unit_id) + struct.pack('B', func_code)
                resp += struct.pack('B', len(response_data))
                conn.sendall(resp + bytes(response_data))
            else:
                resp = struct.pack('>H', trans_id) + struct.pack('>H', proto_id)
                resp += struct.pack('>H', 3) + struct.pack('B', unit_id)
                resp += struct.pack('B', func_code | 0x80) + struct.pack('B', 1)
                conn.sendall(resp)
    except (ConnectionResetError, ConnectionAbortedError, OSError):
        pass
    finally:
        conn.close()

def data_updater():
    while True:
        generate_data()
        time.sleep(update_interval)

def main():
    load_config()

    print("=" * 60, flush=True)
    print("  智慧水厂 Modbus TCP 模拟器", flush=True)
    print(f"  监听: {listen_host}:{listen_port}", flush=True)
    print(f"  更新间隔: {update_interval}s", flush=True)
    print(f"  原水参数: 浊度={params.get('raw_water',{}).get('turbidity',18):.1f} "
          f"pH={params.get('raw_water',{}).get('ph',7.1):.1f} "
          f"温度={params.get('raw_water',{}).get('temperature',18):.1f} "
          f"流量={params.get('raw_water',{}).get('flow_rate',8333):.0f}", flush=True)
    print(f"  波动范围: 浊度±{fluctuation.get('turbidity',2):.1f} "
          f"pH±{fluctuation.get('ph',0.1):.2f} "
          f"温度±{fluctuation.get('temperature',0.5):.1f} "
          f"流量±{fluctuation.get('flow_rate',200):.0f}", flush=True)
    print("  寄存器地址:", flush=True)
    print("    原水: 0x0000  絮凝: 0x0014  沉淀: 0x0028", flush=True)
    print("    滤池: 0x003C  出水: 0x0050", flush=True)
    print("=" * 60, flush=True)

    generate_data()

    updater = threading.Thread(target=data_updater, daemon=True)
    updater.start()

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((listen_host, listen_port))
    server.listen(5)

    try:
        while True:
            conn, addr = server.accept()
            threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()
    except KeyboardInterrupt:
        print("\n模拟器已停止", flush=True)
    finally:
        server.close()

if __name__ == '__main__':
    main()
