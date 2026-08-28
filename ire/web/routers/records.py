from __future__ import annotations

from fastapi import APIRouter, Depends, File, Form, Request, UploadFile
from fastapi.templating import Jinja2Templates

from ire.web.batch import parse_batch_upload
from ire.web.dependencies import WebRuntime, get_runtime
from ire.web.presenters import present_batch_results, present_process_result

router = APIRouter(tags=["records"])


def _templates(request: Request) -> Jinja2Templates:
    return request.app.state.templates


@router.get("/identities/new")
def new_identity_form(request: Request):
    return _templates(request).TemplateResponse(
        request,
        "ingest.html",
        {"mode": "single", "form_data": {}, "batch_result": None, "result": None},
    )


@router.post("/identities/new")
def submit_identity(
    request: Request,
    source_system: str = Form(...),
    source_pk: str = Form(...),
    hkid: str = Form(default=""),
    emplid: str = Form(default=""),
    student_id: str = Form(default=""),
    alumni_id: str = Form(default=""),
    first_name: str = Form(default=""),
    last_name: str = Form(default=""),
    email: str = Form(default=""),
    phone: str = Form(default=""),
    date_of_birth: str = Form(default=""),
    gender: str = Form(default=""),
    address: str = Form(default=""),
    runtime: WebRuntime = Depends(get_runtime),
):
    form_data = {
        "source_system": source_system,
        "source_pk": source_pk,
        "hkid": hkid,
        "emplid": emplid,
        "student_id": student_id,
        "alumni_id": alumni_id,
        "first_name": first_name,
        "last_name": last_name,
        "email": email,
        "phone": phone,
        "date_of_birth": date_of_birth,
        "gender": gender,
        "address": address,
    }
    payload = {
        "source_system": source_system,
        "source_pk": source_pk,
        "data": {key: value for key, value in form_data.items() if key not in {"source_system", "source_pk"} and value not in (None, "")},
    }
    result = runtime.process_record_fn(payload, runtime.config, runtime.repo)
    return _templates(request).TemplateResponse(
        request,
        "match_result.html",
        {"result": present_process_result(result, runtime.repo), "form_data": form_data},
    )


@router.get("/identities/batch")
def batch_form(request: Request):
    return _templates(request).TemplateResponse(
        request,
        "ingest.html",
        {"mode": "batch", "form_data": {}, "batch_result": None, "result": None},
    )


@router.post("/identities/batch")
async def submit_batch(request: Request, file: UploadFile = File(...), runtime: WebRuntime = Depends(get_runtime)):
    records = parse_batch_upload(file.filename, file.content_type, await file.read())
    results = runtime.process_batch_fn(records, runtime.config, runtime.repo)
    batch_result = present_batch_results(results, runtime.repo, total_records=len(records), filename=file.filename)
    return _templates(request).TemplateResponse(
        request,
        "ingest.html",
        {"mode": "batch", "form_data": {}, "batch_result": batch_result, "result": None},
    )
